package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.tiendas.archivos.Almacenamiento;
import com.tiendas.comun.NoEncontradoException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Emite comprobantes ante ARCA vía AFRelay. Sin {@code @Transactional} a propósito: cada cambio de
 * estado se guarda en el momento, así un rechazo de ARCA queda registrado como ERROR (dentro de una
 * transacción, la excepción lo revertiría) y no se mantiene una conexión a la base durante la llamada HTTP.
 */
@Service
@FacturacionHabilitada
public class ComprobanteService {

	private static final DateTimeFormatter FECHA_ARCA = DateTimeFormatter.BASIC_ISO_DATE;
	private static final int ESCALA = 2;

	private final ComprobanteRepository comprobanteRepository;
	private final ComprobanteFiscalValidator validador;
	private final AfRelayClient afRelay;
	private final ComprobantePdfService pdfService;
	private final Almacenamiento almacenamiento;
	private final JsonMapper jsonMapper;
	private final FacturacionProperties facturacion;

	/**
	 * La numeración es "último autorizado + 1": dos emisiones simultáneas obtendrían el mismo número.
	 * Hay una sola instancia por tienda, así que un lock en memoria alcanza para serializarlas.
	 */
	private final ReentrantLock emision = new ReentrantLock();

	ComprobanteService(ComprobanteRepository comprobanteRepository, ComprobanteFiscalValidator validador,
			AfRelayClient afRelay, ComprobantePdfService pdfService, Almacenamiento almacenamiento,
			JsonMapper jsonMapper, FacturacionProperties facturacion) {
		this.comprobanteRepository = comprobanteRepository;
		this.validador = validador;
		this.afRelay = afRelay;
		this.pdfService = pdfService;
		this.almacenamiento = almacenamiento;
		this.jsonMapper = jsonMapper;
		this.facturacion = facturacion;
	}

	// ─── Consultas ──────────────────────────────────────────────────────

	public Page<Comprobante> listar(Pageable pagina) {
		return comprobanteRepository.findAll(PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
				Sort.by(Sort.Direction.DESC, "creadoEn")));
	}

	public Comprobante obtener(Long id) {
		return comprobanteRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Comprobante", id));
	}

	public Comprobante obtenerPorIdempotencyKey(String idempotencyKey) {
		return comprobanteRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new NoEncontradoException("Comprobante no encontrado"));
	}

	/** PDF de un comprobante autorizado. Se genera la primera vez que se pide y queda guardado. */
	public byte[] obtenerPdf(UUID codigo) {
		Comprobante comprobante = comprobanteRepository.findByCodigo(codigo)
				.filter(c -> c.getEstado() == EstadoComprobante.AUTORIZADO)
				.orElseThrow(() -> new NoEncontradoException("Comprobante no encontrado"));
		if (comprobante.getPdfClave() != null) {
			var guardado = almacenamiento.leer(comprobante.getPdfClave());
			if (guardado.isPresent()) {
				return guardado.get().contenido();
			}
		}
		byte[] pdf = pdfService.generar(comprobante);
		String clave = "comprobantes/" + comprobante.getCodigo() + ".pdf";
		almacenamiento.guardar(clave, pdf, "application/pdf");
		comprobante.setPdfClave(clave);
		comprobanteRepository.save(comprobante);
		return pdf;
	}

	// ─── Emisión ────────────────────────────────────────────────────────

	public Comprobante generarManual(ComprobanteManualSolicitud manual) {
		return generar(completarSolicitudManual(manual));
	}

	/** Emite un comprobante. Es idempotente por {@code idempotencyKey}: reintentar no duplica facturas. */
	public Comprobante generar(ComprobanteSolicitud solicitud) {
		validador.validar(solicitud);
		emision.lock();
		try {
			var existente = comprobanteRepository.findByIdempotencyKey(solicitud.getIdempotencyKey());
			if (existente.isPresent() && existente.get().getEstado() != EstadoComprobante.ERROR) {
				return existente.get();
			}

			Comprobante comprobante = existente.orElseGet(Comprobante::new);
			if (comprobante.getCodigo() == null) {
				comprobante.setCodigo(UUID.randomUUID());
			}
			comprobante.setIdempotencyKey(solicitud.getIdempotencyKey());
			comprobante.setSolicitud(solicitud);
			comprobante.setTipo(solicitud.getTipoComprobante());
			comprobante.setPuntoVenta(solicitud.getPuntoVenta());
			comprobante.setTotal(solicitud.getImportes().getTotal());
			comprobante.setEstado(EstadoComprobante.RECIBIDO);
			comprobante.setError(null);
			comprobante = comprobanteRepository.save(comprobante);

			try {
				long numero = proximoNumero(solicitud);
				comprobante.setNumero(numero);
				comprobante.setEstado(EstadoComprobante.VALIDADO);

				Map<String, Object> request = armarSolicitudAfRelay(solicitud, numero);
				JsonNode respuesta = afRelay.solicitarCae(request);
				comprobante.setAfrelayRequest(jsonMapper.writeValueAsString(request));
				comprobante.setAfrelayResponse(jsonMapper.writeValueAsString(respuesta));
				comprobante.setCae(buscarTexto(respuesta, "CAE", "cae"));
				comprobante.setCaeVencimiento(parsearFecha(buscarTexto(respuesta, "CAEFchVto", "caeFchVto")));
				comprobante.setResultado(buscarTexto(respuesta, "Resultado", "resultado"));

				if (!"A".equalsIgnoreCase(comprobante.getResultado())) {
					throw new ComprobanteException("ARCA no autorizó el comprobante (resultado "
							+ comprobante.getResultado() + "): " + mensajesArca(respuesta));
				}
				comprobante.setEstado(EstadoComprobante.AUTORIZADO);
				comprobante.setAutorizadoEn(Instant.now());
				return comprobanteRepository.save(comprobante);
			} catch (AfRelayException | ComprobanteException e) {
				comprobante.setEstado(EstadoComprobante.ERROR);
				comprobante.setError(e.getMessage());
				comprobanteRepository.save(comprobante);
				throw e;
			}
		} finally {
			emision.unlock();
		}
	}

	private long proximoNumero(ComprobanteSolicitud solicitud) {
		JsonNode respuesta = afRelay.obtenerUltimoAutorizado(cuit(solicitud.getEmisor().getCuit()),
				solicitud.getPuntoVenta(), solicitud.getTipoComprobante());
		for (String campo : List.of("CbteNro", "cbteNro", "nroComprobante", "ultimoAutorizado")) {
			JsonNode ultimo = respuesta.findValue(campo);
			if (ultimo != null && ultimo.canConvertToLong()) {
				return ultimo.asLong() + 1;
			}
		}
		return 1;
	}

	// Completa los datos fijos y calculables que el panel no necesita enviar
	private ComprobanteSolicitud completarSolicitudManual(ComprobanteManualSolicitud manual) {
		if (manual == null || manual.getCliente() == null) {
			throw new ComprobanteException("cliente es obligatorio");
		}
		ComprobanteSolicitud.DatosReceptor cliente = manual.getCliente();
		if (cliente.getCondicionIva() == null) {
			cliente.setCondicionIva(CondicionIva.CONSUMIDOR_FINAL);
		}
		if (cliente.getTipoDocumento() == null) {
			cliente.setTipoDocumento(TipoDocumento.CONSUMIDOR_FINAL);
		}

		ComprobanteSolicitud solicitud = new ComprobanteSolicitud();
		solicitud.setIdempotencyKey(manual.getIdempotencyKey() == null || manual.getIdempotencyKey().isBlank()
				? "MANUAL-" + UUID.randomUUID()
				: manual.getIdempotencyKey());
		solicitud.setEmisor(emisorConfigurado());
		solicitud.setCliente(cliente);
		solicitud.setTipoComprobante(facturacion.tipoComprobante());
		solicitud.setPuntoVenta(facturacion.puntoVenta());
		solicitud.setConcepto(1); // productos
		solicitud.setFecha(manual.getFecha() == null ? LocalDate.now() : manual.getFecha());
		solicitud.setMoneda(facturacion.moneda());
		solicitud.setCotizacion(facturacion.cotizacion());
		calcularItemsEImportes(manual.getItems(), solicitud);
		return solicitud;
	}

	private ComprobanteSolicitud.DatosEmisor emisorConfigurado() {
		FacturacionProperties.Emisor configurado = facturacion.emisor();
		ComprobanteSolicitud.DatosEmisor emisor = new ComprobanteSolicitud.DatosEmisor();
		emisor.setCuit(configurado.cuit());
		emisor.setRazonSocial(configurado.razonSocial());
		emisor.setCondicionIva(configurado.condicionIva());
		emisor.setDomicilio(configurado.domicilio());
		emisor.setIngresosBrutos(configurado.ingresosBrutos());
		emisor.setInicioActividades(configurado.inicioActividades());
		return emisor;
	}

	/** Los precios del panel son finales (IVA incluido): para A/B se discrimina el IVA, para C no. */
	private void calcularItemsEImportes(List<ComprobanteManualSolicitud.Item> manuales, ComprobanteSolicitud solicitud) {
		if (manuales == null || manuales.isEmpty()) {
			throw new ComprobanteException("items no puede estar vacío");
		}
		boolean claseC = facturacion.tipoComprobante().esClaseC();
		AlicuotaIva alicuota = claseC ? null : facturacion.alicuotaIvaDefault();

		List<ComprobanteSolicitud.Item> items = new ArrayList<>();
		BigDecimal neto = BigDecimal.ZERO;
		BigDecimal iva = BigDecimal.ZERO;
		BigDecimal total = BigDecimal.ZERO;
		for (ComprobanteManualSolicitud.Item manual : manuales) {
			if (manual == null || manual.getPrecioUnitario() == null || manual.getCantidad() == null) {
				throw new ComprobanteException("Cada item debe tener cantidad y precioUnitario");
			}
			ComprobanteSolicitud.Item item = new ComprobanteSolicitud.Item();
			item.setDescripcion(manual.getDescripcion());
			item.setCantidad(manual.getCantidad());
			item.setPrecioUnitario(redondear(manual.getPrecioUnitario()));
			item.setAlicuotaIva(alicuota);

			BigDecimal subtotal = redondear(item.getPrecioUnitario().multiply(item.getCantidad()));
			BigDecimal netoItem = claseC ? subtotal : netoDesdePrecioFinal(subtotal, alicuota);
			BigDecimal ivaItem = claseC ? BigDecimal.ZERO.setScale(ESCALA) : redondear(subtotal.subtract(netoItem));
			item.setSubtotal(subtotal);
			item.setImporteIva(ivaItem);
			items.add(item);

			neto = neto.add(netoItem);
			iva = iva.add(ivaItem);
			total = total.add(subtotal);
		}

		ComprobanteSolicitud.Importes importes = new ComprobanteSolicitud.Importes();
		importes.setNeto(redondear(neto));
		importes.setIva(redondear(iva));
		importes.setTributos(BigDecimal.ZERO.setScale(ESCALA));
		importes.setExento(BigDecimal.ZERO.setScale(ESCALA));
		importes.setNoGravado(BigDecimal.ZERO.setScale(ESCALA));
		importes.setTotal(redondear(total));
		solicitud.setItems(items);
		solicitud.setImportes(importes);
	}

	private Map<String, Object> armarSolicitudAfRelay(ComprobanteSolicitud solicitud, long numero) {
		ComprobanteSolicitud.Importes importes = solicitud.getImportes();
		BigDecimal neto = orZero(importes.getNeto());
		BigDecimal iva = orZero(importes.getIva());

		Map<String, Object> detalle = new LinkedHashMap<>();
		detalle.put("Concepto", solicitud.getConcepto() == null ? 1 : solicitud.getConcepto());
		detalle.put("DocTipo", tipoDocumento(solicitud).getCodigoArca());
		detalle.put("DocNro", numeroDocumento(solicitud));
		detalle.put("CbteDesde", numero);
		detalle.put("CbteHasta", numero);
		detalle.put("CbteFch", FECHA_ARCA.format(solicitud.getFecha()));
		detalle.put("ImpTotal", importes.getTotal());
		detalle.put("ImpTotConc", orZero(importes.getNoGravado()));
		detalle.put("ImpNeto", neto);
		detalle.put("ImpOpEx", orZero(importes.getExento()));
		detalle.put("ImpTrib", orZero(importes.getTributos()));
		detalle.put("ImpIVA", iva);
		detalle.put("MonId", solicitud.getMoneda() == null ? "PES" : solicitud.getMoneda());
		detalle.put("MonCotiz", solicitud.getCotizacion() == null ? BigDecimal.ONE : solicitud.getCotizacion());
		CondicionIva condicionReceptor = solicitud.getCliente().getCondicionIva();
		detalle.put("CondicionIVAReceptorId",
				(condicionReceptor == null ? CondicionIva.CONSUMIDOR_FINAL : condicionReceptor).getCodigoArca());
		if (iva.compareTo(BigDecimal.ZERO) > 0) {
			detalle.put("Iva", Map.of("AlicIva", List.of(Map.of(
					"Id", facturacion.alicuotaIvaDefault().getCodigoArca(),
					"BaseImp", neto,
					"Importe", iva))));
		}

		Map<String, Object> cabecera = new LinkedHashMap<>();
		cabecera.put("CantReg", 1);
		cabecera.put("PtoVta", solicitud.getPuntoVenta());
		cabecera.put("CbteTipo", solicitud.getTipoComprobante().getCodigoArca());

		return Map.of(
				"Auth", Map.of("Cuit", cuit(solicitud.getEmisor().getCuit())),
				"FeCAEReq", Map.of("FeCabReq", cabecera, "FeDetReq", Map.of("FECAEDetRequest", List.of(detalle))));
	}

	private static TipoDocumento tipoDocumento(ComprobanteSolicitud solicitud) {
		TipoDocumento tipo = solicitud.getCliente().getTipoDocumento();
		return tipo == null ? TipoDocumento.CONSUMIDOR_FINAL : tipo;
	}

	private static long numeroDocumento(ComprobanteSolicitud solicitud) {
		String numero = solicitud.getCliente().getNumeroDocumento();
		if (tipoDocumento(solicitud) == TipoDocumento.CONSUMIDOR_FINAL || numero == null || numero.isBlank()) {
			return 0L;
		}
		return Long.parseLong(soloDigitos(numero));
	}

	/** Junta los mensajes de errores y observaciones que devolvió ARCA (campos "Msg"). */
	private static String mensajesArca(JsonNode respuesta) {
		List<String> mensajes = new ArrayList<>();
		respuesta.findValues("Msg").forEach(msg -> mensajes.add(msg.asString()));
		return mensajes.isEmpty() ? "sin detalle" : String.join(" | ", mensajes);
	}

	private static String buscarTexto(JsonNode nodo, String... campos) {
		for (String campo : campos) {
			JsonNode valor = nodo == null ? null : nodo.findValue(campo);
			if (valor != null && !valor.isNull()) {
				return valor.asString();
			}
		}
		return null;
	}

	private static LocalDate parsearFecha(String valor) {
		return valor == null || valor.isBlank() ? null : LocalDate.parse(valor, FECHA_ARCA);
	}

	private static long cuit(String cuit) {
		return Long.parseLong(soloDigitos(cuit));
	}

	private static String soloDigitos(String valor) {
		return valor == null ? "" : valor.replaceAll("\\D", "");
	}

	private static BigDecimal netoDesdePrecioFinal(BigDecimal precioFinal, AlicuotaIva alicuota) {
		BigDecimal divisor = BigDecimal.ONE.add(alicuota.getPorcentaje().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
		return precioFinal.divide(divisor, ESCALA, RoundingMode.HALF_UP);
	}

	private static BigDecimal redondear(BigDecimal valor) {
		return valor.setScale(ESCALA, RoundingMode.HALF_UP);
	}

	private static BigDecimal orZero(BigDecimal valor) {
		return valor == null ? BigDecimal.ZERO : valor;
	}
}
