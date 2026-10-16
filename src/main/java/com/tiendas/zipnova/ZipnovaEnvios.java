package com.tiendas.zipnova;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.envios.EnvioService;
import com.tiendas.pedidos.DatosCliente;
import com.tiendas.pedidos.DireccionEnvio;
import com.tiendas.pedidos.EstadoPedido;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoItem;
import com.tiendas.pedidos.PedidoRepository;
import com.tiendas.pedidos.PedidoService;
import com.tiendas.pedidos.TipoEntrega;

/**
 * Lo que el vendedor hace con Zipnova desde una venta: crear el envío (lo cobra Zipnova de su saldo), bajar la
 * etiqueta para pegar en el paquete y consultar el seguimiento.
 * <p>
 * Las llamadas a Zipnova van fuera de transacción; solo se abre una para guardar el resultado.
 */
@Service
@ConditionalOnBooleanProperty("zipnova.habilitada")
public class ZipnovaEnvios {

	public record Seguimiento(Pedido pedido, List<Zipnova.EventoSeguimiento> eventos) {
	}

	public record Despacho(List<Zipnova.Punto> puntos) {
	}

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ZipnovaEnvios.class);

	private final Zipnova zipnova;
	private final PedidoService pedidoService;
	private final PedidoRepository pedidoRepository;
	private final EnvioService envioService;
	private final TransactionTemplate transaccion;

	ZipnovaEnvios(Zipnova zipnova, PedidoService pedidoService, PedidoRepository pedidoRepository,
			EnvioService envioService, TransactionTemplate transaccion) {
		this.zipnova = zipnova;
		this.pedidoService = pedidoService;
		this.pedidoRepository = pedidoRepository;
		this.envioService = envioService;
		this.transaccion = transaccion;
	}

	public Pedido darDeAlta(Long pedidoId) {
		Pedido pedido = pedidoService.obtener(pedidoId);
		if (pedido.getEstado() != EstadoPedido.APROBADO) {
			throw new ReglaNegocioException("Solo se pueden enviar ventas pagadas");
		}
		if (pedido.getEntrega() == TipoEntrega.RETIRO) {
			throw new ReglaNegocioException("La venta es para retirar en el local: no hay nada que enviar");
		}
		if (pedido.getEnvioExternoId() != null) {
			throw new ConflictoException("El envío de esta venta ya se creó en Zipnova");
		}
		EnvioService.ServicioZipnova servicio = EnvioService.servicioZipnova(pedido.getEnvioOpcion()).orElseThrow(() ->
				new ReglaNegocioException("El cliente no eligió un envío de Zipnova: crealo desde el panel de Zipnova"));

		Map<Long, BigDecimal> cantidades = pedido.getItems().stream()
				.collect(Collectors.toMap(PedidoItem::getVarianteId, PedidoItem::getCantidad, BigDecimal::add));
		List<Zipnova.Item> items = envioService.items(cantidades).orElseThrow(() ->
				new ReglaNegocioException("Faltan el peso o las medidas de algún producto de la venta: cargalos en el "
						+ "artículo o en su categoría"));

		DatosCliente cliente = pedido.getCliente();
		if (!StringUtils.hasText(cliente.getDocumento()) || !StringUtils.hasText(cliente.getTelefono())) {
			throw new ReglaNegocioException("Zipnova pide el DNI y el teléfono del cliente: la venta no los tiene");
		}
		boolean enPunto = pedido.getEntrega() == TipoEntrega.SUCURSAL;
		DireccionEnvio d = enPunto ? new DireccionEnvio() : pedido.getDireccion();
		if (enPunto && !StringUtils.hasText(pedido.getEnvioSucursal())) {
			throw new ReglaNegocioException("La venta no tiene la sucursal donde retira el cliente");
		}

		String codigo = pedido.getCodigo().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		Zipnova.Envio envio = zipnova.darDeAlta(new Zipnova.AltaEnvio(
				"P" + pedido.getId() + "-" + codigo,
				servicio.servicio(),
				servicio.transportistaId(),
				pedido.getEnvioLogistica(),
				enPunto ? pedido.getEnvioSucursal() : null,
				cliente.getNombre() + " " + cliente.getApellido(),
				cliente.getDocumento().replaceAll("\\D", ""),
				cliente.getEmail(),
				cliente.getTelefono(),
				d.getCalle(),
				d.getNumero(),
				extras(d),
				d.getCiudad(),
				d.getProvincia(),
				d.getCodigoPostal(),
				items,
				pedido.getSubtotal()));

		transaccion.executeWithoutResult(estado -> {
			Pedido guardado = buscar(pedidoId);
			guardado.setEnvioExternoId(envio.id());
			guardado.setEnvioAltaEn(Instant.now());
			copiar(envio, guardado);
		});
		return pedidoService.obtener(pedidoId);
	}

	public byte[] etiqueta(Long pedidoId) {
		return zipnova.etiqueta(idEnZipnova(pedidoService.obtener(pedidoId)));
	}

	public byte[] guia(Long pedidoId) {
		return zipnova.guia(idEnZipnova(pedidoService.obtener(pedidoId))).orElseThrow(() ->
				new ReglaNegocioException("Este transporte no usa guía de despacho: alcanza con la etiqueta"));
	}

	/** Dónde llevar el paquete (puntos del transporte cerca de tu origen). */
	public Despacho despacho(Long pedidoId) {
		return new Despacho(zipnova.puntosDeDespacho(idEnZipnova(pedidoService.obtener(pedidoId))));
	}

	/**
	 * Cancela el envío en Zipnova. Si todavía no se despachó, la venta queda como si no se hubiera creado (se puede
	 * volver a crear); si ya se despachó, Zipnova le pide el rescate al transporte y la venta conserva el envío.
	 */
	public Pedido cancelar(Long pedidoId) {
		String id = idEnZipnova(pedidoService.obtener(pedidoId));
		Zipnova.Cancelacion cancelacion = zipnova.cancelar(id);
		transaccion.executeWithoutResult(estado -> {
			Pedido pedido = buscar(pedidoId);
			if (cancelacion.rescate()) {
				pedido.setEnvioEstado("Rescate pedido al transporte");
			} else {
				pedido.setEnvioExternoId(null);
				pedido.setEnvioAltaEn(null);
				pedido.setEnvioEstado(null);
				pedido.setEnvioSeguimiento(null);
				pedido.setEnvioSeguimientoUrl(null);
				pedido.setEnvioTransportista(null);
			}
		});
		return pedidoService.obtener(pedidoId);
	}

	public Zipnova.Saldo saldo() {
		return zipnova.saldo();
	}

	/**
	 * Aviso de Zipnova (webhook) de que un envío cambió. El cuerpo no se toma como dato: solo dice qué envío
	 * consultar, y el estado se lee de la API (Zipnova no firma los avisos).
	 */
	public void avisoDeEnvio(String envioId) {
		pedidoRepository.findByEnvioExternoId(envioId).ifPresentOrElse(pedido -> {
			Zipnova.Envio envio = zipnova.envio(envioId);
			transaccion.executeWithoutResult(estado -> copiar(envio, buscar(pedido.getId())));
		}, () -> log.debug("Aviso de Zipnova de un envío que no es de esta tienda: {}", envioId));
	}

	/** El historial del envío y la venta con el estado actualizado (transporte, código y estado). */
	public Seguimiento seguimiento(Long pedidoId) {
		String id = idEnZipnova(pedidoService.obtener(pedidoId));
		Zipnova.Envio envio = zipnova.envio(id);
		List<Zipnova.EventoSeguimiento> eventos = zipnova.seguimiento(id);
		transaccion.executeWithoutResult(estado -> copiar(envio, buscar(pedidoId)));
		return new Seguimiento(pedidoService.obtener(pedidoId), eventos);
	}

	private static String idEnZipnova(Pedido pedido) {
		if (pedido.getEnvioExternoId() == null) {
			throw new ReglaNegocioException("Primero creá el envío en Zipnova");
		}
		return pedido.getEnvioExternoId();
	}

	private static void copiar(Zipnova.Envio envio, Pedido pedido) {
		pedido.setEnvioEstado(recortar(envio.estado(), 80));
		if (StringUtils.hasText(envio.transportista())) {
			pedido.setEnvioTransportista(recortar(envio.transportista(), 80));
		}
		if (StringUtils.hasText(envio.codigoSeguimiento())) {
			pedido.setEnvioSeguimiento(recortar(envio.codigoSeguimiento(), 60));
		}
		if (StringUtils.hasText(envio.urlSeguimiento())) {
			pedido.setEnvioSeguimientoUrl(recortar(envio.urlSeguimiento(), 300));
		}
	}

	/** Piso, departamento e indicaciones en una línea (máx. 190, lo que acepta Zipnova), ej. "Piso 3 Dto B. Timbre 2". */
	static String extras(DireccionEnvio d) {
		String unidad = ((StringUtils.hasText(d.getPiso()) ? "Piso " + d.getPiso().trim() + " " : "")
				+ (StringUtils.hasText(d.getDepartamento()) ? "Dto " + d.getDepartamento().trim() : "")).trim();
		String indicaciones = StringUtils.hasText(d.getIndicaciones()) ? d.getIndicaciones().trim() : "";
		String extras = unidad.isEmpty() ? indicaciones : indicaciones.isEmpty() ? unidad : unidad + ". " + indicaciones;
		return extras.isEmpty() ? null : recortar(extras, 190);
	}

	private Pedido buscar(Long id) {
		return pedidoRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Pedido", id));
	}

	private static String recortar(String texto, int maximo) {
		return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
