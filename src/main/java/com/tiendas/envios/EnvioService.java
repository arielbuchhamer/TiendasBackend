package com.tiendas.envios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tiendas.catalogo.Categoria;
import com.tiendas.catalogo.CategoriaRepository;
import com.tiendas.catalogo.Medidas;
import com.tiendas.catalogo.Variante;
import com.tiendas.catalogo.VarianteRepository;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.correo.CorreoArgentino;

/**
 * Opciones de entrega para un código postal: retiro en el local y envío según la zona. Una zona tiene precio fijo
 * o se cotiza con Correo Argentino (a domicilio y a sucursal); si el Correo no responde o faltan medidas, se usa el
 * precio de respaldo de la zona o el costo queda a convenir, para no perder la venta.
 * El checkout llama a {@link #elegir} y el precio del envío siempre sale de acá, nunca del cliente.
 * <p>
 * Los códigos postales se comparan por sus 4 dígitos (también acepta el formato CPA, ej. "A3164ABC").
 */
@Service
@Transactional(readOnly = true)
public class EnvioService {

	private static final Logger log = LoggerFactory.getLogger(EnvioService.class);

	public static final String RETIRO = "RETIRO";
	private static final String PREFIJO_ZONA = "ZONA-";
	private static final String PREFIJO_CORREO = "CORREO-";
	private static final Pattern CUATRO_DIGITOS = Pattern.compile("(\\d{4})");
	private static final Pattern TRAMO = Pattern.compile("(\\d{4})(?:\\s*-\\s*(\\d{4}))?");

	/** Cómo recibe el cliente la compra. */
	public enum Modalidad {
		RETIRO, DOMICILIO, SUCURSAL
	}

	/**
	 * Una forma de recibir la compra. {@code precio} ya tiene aplicado el envío gratis por monto; es cero si
	 * el costo es a convenir (no se cobra en la web). {@code titulo} es el texto para el cliente, ej.
	 * "Envío a domicilio · Correo Argentino Clásico".
	 */
	public record OpcionEnvio(String id, Modalidad modalidad, boolean retiro, String titulo, String nombre,
			BigDecimal precio, boolean costoAConvenir, boolean gratisPorMonto, Integer diasMin, Integer diasMax,
			String detalle) {

		/** Texto que queda guardado en el pedido, ej. "Envío a domicilio · Entre Ríos (2 a 4 días hábiles)". */
		public String descripcion() {
			String plazo = plazo(diasMin, diasMax);
			return plazo == null ? titulo : titulo + " (" + plazo + ")";
		}
	}

	/** La opción elegida en el checkout y, si es retiro en sucursal del Correo, la sucursal. */
	public record EnvioElegido(OpcionEnvio opcion, CorreoArgentino.Sucursal sucursal) {
	}

	public record Cotizacion(String codigoPostal, BigDecimal subtotal, BigDecimal envioGratisDesde,
			List<OpcionEnvio> opciones) {
	}

	/** Lo que el frontend necesita saber antes de que el cliente escriba su código postal. */
	public record InfoEnvios(boolean retiroHabilitado, String retiroDetalle, boolean domicilioHabilitado,
			BigDecimal envioGratisDesde) {
	}

	private final ZonaEnvioRepository zonaRepository;
	private final AjustesEnvioRepository ajustesRepository;
	private final VarianteRepository varianteRepository;
	private final CategoriaRepository categoriaRepository;
	private final EnviosProperties envios;
	private final ObjectProvider<CorreoArgentino> correo;

	public EnvioService(ZonaEnvioRepository zonaRepository, AjustesEnvioRepository ajustesRepository,
			VarianteRepository varianteRepository, CategoriaRepository categoriaRepository, EnviosProperties envios,
			ObjectProvider<CorreoArgentino> correo) {
		this.zonaRepository = zonaRepository;
		this.ajustesRepository = ajustesRepository;
		this.varianteRepository = varianteRepository;
		this.categoriaRepository = categoriaRepository;
		this.envios = envios;
		this.correo = correo;
	}

	public boolean habilitado() {
		return envios.habilitada();
	}

	// ─── Tienda y checkout ──────────────────────────────────────────────

	public InfoEnvios info() {
		AjustesEnvio ajustes = ajustes();
		return new InfoEnvios(ajustes.isRetiroHabilitado(), ajustes.getRetiroDetalle(),
				!zonaRepository.findByActivaTrueOrderByOrdenAscIdAsc().isEmpty(), ajustes.getEnvioGratisDesde());
	}

	/** Opciones para un carrito (variante → cantidad). Sin código postal, solo el retiro. */
	public Cotizacion cotizar(String codigoPostal, Map<Long, BigDecimal> cantidades) {
		BigDecimal subtotal = varianteRepository.buscarConProducto(cantidades.keySet()).stream()
				.filter(v -> v.isActivo() && v.getProducto().isActivo())
				.map(v -> precioDe(v, cantidades.get(v.getId())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Cotizacion(StringUtils.hasText(codigoPostal) ? normalizar(codigoPostal) : null, subtotal,
				ajustes().getEnvioGratisDesde(), opciones(codigoPostal, cantidades, subtotal));
	}

	public List<OpcionEnvio> opciones(String codigoPostal, Map<Long, BigDecimal> cantidades, BigDecimal subtotal) {
		AjustesEnvio ajustes = ajustes();
		List<OpcionEnvio> opciones = new ArrayList<>();
		if (ajustes.isRetiroHabilitado()) {
			opciones.add(new OpcionEnvio(RETIRO, Modalidad.RETIRO, true, "Retiro en el local", "Retiro en el local",
					BigDecimal.ZERO, false, false, null, null, ajustes.getRetiroDetalle()));
		}
		if (StringUtils.hasText(codigoPostal)) {
			String cp = normalizar(codigoPostal);
			zonaPara(cp).ifPresent(zona -> opciones.addAll(zona.getTipo() == TipoZona.CORREO_ARGENTINO
					? opcionesCorreo(zona, cp, cantidades, subtotal, ajustes)
					: List.of(opcionDe(zona, subtotal, ajustes))));
		}
		return opciones;
	}

	/**
	 * Valida la entrega elegida en el checkout y devuelve la opción con su precio.
	 *
	 * @param opcionId       opción que eligió el cliente; si es nula se toma la primera disponible
	 * @param sucursalCodigo sucursal del Correo donde retira (solo modalidad SUCURSAL); su código postal define
	 *                       el precio
	 */
	public EnvioElegido elegir(Modalidad modalidad, String codigoPostal, String opcionId, String sucursalCodigo,
			BigDecimal subtotal, Map<Long, BigDecimal> cantidades) {
		if (modalidad == Modalidad.RETIRO) {
			if (!ajustes().isRetiroHabilitado()) {
				throw new ReglaNegocioException("La tienda no permite retirar los pedidos");
			}
			return new EnvioElegido(opciones(null, cantidades, subtotal).getFirst(), null);
		}

		CorreoArgentino.Sucursal sucursal = null;
		String cp = StringUtils.hasText(codigoPostal) ? normalizar(codigoPostal) : null;
		if (modalidad == Modalidad.SUCURSAL) {
			sucursal = sucursal(sucursalCodigo);
			if (sucursal.codigoPostal() != null) {
				cp = normalizar(sucursal.codigoPostal());
			}
		}
		if (cp == null) {
			throw new ReglaNegocioException("Falta el código postal para calcular el envío");
		}
		String destino = cp;
		OpcionEnvio opcion = opciones(cp, cantidades, subtotal).stream()
				.filter(o -> o.modalidad() == modalidad)
				.filter(o -> opcionId == null || o.id().equals(opcionId))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException(opcionId == null
						? "No hay envíos " + (modalidad == Modalidad.SUCURSAL ? "a sucursal" : "a domicilio")
								+ " para el código postal " + destino + ". Consultanos por WhatsApp."
						: "La opción de envío elegida ya no está disponible para el código postal " + destino));
		return new EnvioElegido(opcion, sucursal);
	}

	/** Sucursales del Correo de una provincia donde se puede retirar (vacío si no está integrado el Correo). */
	public List<CorreoArgentino.Sucursal> sucursales(String codigoProvincia) {
		CorreoArgentino cliente = correo.getIfAvailable();
		return cliente == null ? List.of() : cliente.sucursales(codigoProvincia.trim().toUpperCase(Locale.ROOT));
	}

	private CorreoArgentino.Sucursal sucursal(String codigo) {
		CorreoArgentino cliente = correo.getIfAvailable();
		if (cliente == null) {
			throw new ReglaNegocioException("La tienda no hace envíos a sucursales del Correo");
		}
		if (!StringUtils.hasText(codigo)) {
			throw new ReglaNegocioException("Elegí la sucursal del Correo donde vas a retirar");
		}
		String limpio = codigo.trim().toUpperCase(Locale.ROOT);
		// El código de sucursal empieza con la letra de la provincia (ej. "E0101" en Entre Ríos)
		return cliente.sucursales(limpio.substring(0, 1)).stream()
				.filter(s -> s.codigo().equalsIgnoreCase(limpio))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException("La sucursal " + limpio + " no existe o no permite retirar envíos"));
	}

	/**
	 * Bultos del carrito para cotizar con el Correo: peso y medidas de cada producto (o de su categoría). Vacío si
	 * a algún producto le faltan datos, porque cotizar sin ellos daría un precio equivocado.
	 */
	public Optional<List<CorreoArgentino.Paquete>> paquetes(Map<Long, BigDecimal> cantidades) {
		List<Variante> variantes = varianteRepository.buscarConProducto(cantidades.keySet());
		Map<Long, Medidas> porCategoria = categoriaRepository.findAllById(
				variantes.stream().map(v -> v.getProducto().getCategoriaId()).collect(Collectors.toSet())).stream()
				.filter(c -> completas(c.getMedidas()))
				.collect(Collectors.toMap(Categoria::getId, Categoria::getMedidas));
		List<CorreoArgentino.Paquete> paquetes = new ArrayList<>();
		for (Variante variante : variantes) {
			Medidas medidas = completas(variante.getProducto().getMedidas()) ? variante.getProducto().getMedidas()
					: porCategoria.get(variante.getProducto().getCategoriaId());
			if (medidas == null) {
				return Optional.empty();
			}
			int cantidad = cantidades.get(variante.getId()).setScale(0, RoundingMode.CEILING).intValue();
			paquetes.add(new CorreoArgentino.Paquete(Math.max(cantidad, 1), medidas.getPesoGramos(), medidas.getLargoCm(),
					medidas.getAnchoCm(), medidas.getAltoCm()));
		}
		return paquetes.isEmpty() ? Optional.empty() : Optional.of(paquetes);
	}

	private List<OpcionEnvio> opcionesCorreo(ZonaEnvio zona, String cp, Map<Long, BigDecimal> cantidades,
			BigDecimal subtotal, AjustesEnvio ajustes) {
		CorreoArgentino cliente = correo.getIfAvailable();
		Optional<List<CorreoArgentino.Paquete>> paquetes = paquetes(cantidades);
		if (cliente == null || paquetes.isEmpty()) {
			log.warn("Zona {}: no se puede cotizar con el Correo ({}); se usa el respaldo", zona.getNombre(),
					cliente == null ? "Correo Argentino no está habilitado" : "faltan peso y medidas de algún producto");
			return List.of(respaldo(zona, subtotal, ajustes));
		}
		List<CorreoArgentino.Tarifa> tarifas;
		try {
			tarifas = cliente.cotizar(cp, paquetes.get());
		} catch (RuntimeException e) {
			log.warn("Correo Argentino no cotizó el envío a {}: {}. Se usa el respaldo de la zona {}", cp, e.getMessage(),
					zona.getNombre());
			return List.of(respaldo(zona, subtotal, ajustes));
		}
		boolean gratis = gratisPorMonto(subtotal, ajustes);
		List<OpcionEnvio> opciones = tarifas.stream()
				.filter(t -> "D".equals(t.entrega()) ? zona.isCorreoDomicilio() : zona.isCorreoSucursal())
				.filter(t -> "CP".equals(t.servicio()) || zona.isCorreoExpreso())
				.sorted(Comparator.comparing(CorreoArgentino.Tarifa::entrega).thenComparing(CorreoArgentino.Tarifa::servicio))
				.map(t -> {
					String servicio = "EP".equals(t.servicio()) ? "Expreso" : "Clásico";
					boolean domicilio = "D".equals(t.entrega());
					return new OpcionEnvio(PREFIJO_CORREO + zona.getId() + "-" + t.entrega() + "-" + t.servicio(),
							domicilio ? Modalidad.DOMICILIO : Modalidad.SUCURSAL, false,
							domicilio ? "Envío a domicilio · Correo Argentino " + servicio
									: "Retiro en sucursal del Correo · " + servicio,
							"Correo Argentino " + servicio,
							gratis ? BigDecimal.ZERO : t.precio().setScale(2, RoundingMode.HALF_UP), false, gratis,
							t.diasMin(), t.diasMax(), null);
				})
				.toList();
		return opciones.isEmpty() ? List.of(respaldo(zona, subtotal, ajustes)) : opciones;
	}

	/** Si el Correo no puede cotizar: el precio fijo de la zona o, si no tiene, costo a convenir. */
	private static OpcionEnvio respaldo(ZonaEnvio zona, BigDecimal subtotal, AjustesEnvio ajustes) {
		boolean aConvenir = zona.getPrecio() == null;
		boolean gratis = !aConvenir && gratisPorMonto(subtotal, ajustes);
		return new OpcionEnvio(PREFIJO_ZONA + zona.getId(), Modalidad.DOMICILIO, false,
				"Envío a domicilio · " + zona.getNombre(), zona.getNombre(),
				aConvenir || gratis ? BigDecimal.ZERO : zona.getPrecio(), aConvenir, gratis, zona.getDiasMin(),
				zona.getDiasMax(), null);
	}

	private Optional<ZonaEnvio> zonaPara(String cp) {
		int codigo = Integer.parseInt(cp);
		List<ZonaEnvio> activas = zonaRepository.findByActivaTrueOrderByOrdenAscIdAsc();
		// Primero las zonas con códigos postales; la zona sin códigos ("resto del país") va al final
		return activas.stream()
				.filter(z -> StringUtils.hasText(z.getCodigosPostales()))
				.filter(z -> incluye(z.getCodigosPostales(), codigo))
				.findFirst()
				.or(() -> activas.stream().filter(z -> !StringUtils.hasText(z.getCodigosPostales())).findFirst());
	}

	private static OpcionEnvio opcionDe(ZonaEnvio zona, BigDecimal subtotal, AjustesEnvio ajustes) {
		boolean gratis = !zona.isCostoAConvenir() && gratisPorMonto(subtotal, ajustes);
		BigDecimal precio = zona.isCostoAConvenir() || gratis ? BigDecimal.ZERO : zona.getPrecio();
		return new OpcionEnvio(PREFIJO_ZONA + zona.getId(), Modalidad.DOMICILIO, false,
				"Envío a domicilio · " + zona.getNombre(), zona.getNombre(), precio, zona.isCostoAConvenir(), gratis,
				zona.getDiasMin(), zona.getDiasMax(), null);
	}

	private static boolean gratisPorMonto(BigDecimal subtotal, AjustesEnvio ajustes) {
		return ajustes.getEnvioGratisDesde() != null && subtotal.compareTo(ajustes.getEnvioGratisDesde()) >= 0;
	}

	private static boolean completas(Medidas m) {
		return m != null && m.getPesoGramos() != null && m.getLargoCm() != null && m.getAnchoCm() != null
				&& m.getAltoCm() != null;
	}

	private static BigDecimal precioDe(Variante variante, BigDecimal cantidad) {
		return variante.precioEfectivo().multiply(cantidad == null ? BigDecimal.ZERO : cantidad)
				.setScale(2, RoundingMode.HALF_UP);
	}

	// ─── Panel de administración ────────────────────────────────────────

	public AjustesEnvio ajustes() {
		return ajustesRepository.findFirstByOrderByIdAsc().orElseGet(AjustesEnvio::new);
	}

	@Transactional
	public AjustesEnvio guardarAjustes(AjustesEnvio datos) {
		AjustesEnvio ajustes = ajustesRepository.findFirstByOrderByIdAsc().orElseGet(AjustesEnvio::new);
		ajustes.setRetiroHabilitado(datos.isRetiroHabilitado());
		ajustes.setRetiroDetalle(StringUtils.hasText(datos.getRetiroDetalle()) ? datos.getRetiroDetalle().trim() : null);
		ajustes.setEnvioGratisDesde(datos.getEnvioGratisDesde());
		return ajustesRepository.save(ajustes);
	}

	public List<ZonaEnvio> zonas() {
		return zonaRepository.findAllByOrderByOrdenAscIdAsc();
	}

	@Transactional
	public ZonaEnvio crearZona(ZonaEnvio datos) {
		ZonaEnvio zona = new ZonaEnvio();
		copiar(datos, zona);
		return zonaRepository.save(zona);
	}

	@Transactional
	public ZonaEnvio actualizarZona(Long id, ZonaEnvio datos) {
		ZonaEnvio zona = buscarZona(id);
		copiar(datos, zona);
		return zona;
	}

	@Transactional
	public void eliminarZona(Long id) {
		zonaRepository.delete(buscarZona(id));
	}

	private ZonaEnvio buscarZona(Long id) {
		return zonaRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Zona de envío", id));
	}

	private void copiar(ZonaEnvio datos, ZonaEnvio zona) {
		zona.setNombre(datos.getNombre().trim());
		zona.setCodigosPostales(normalizarLista(datos.getCodigosPostales()));
		zona.setTipo(datos.getTipo() == null ? TipoZona.PRECIO_FIJO : datos.getTipo());
		if (zona.getTipo() == TipoZona.CORREO_ARGENTINO) {
			if (correo.getIfAvailable() == null) {
				throw new ReglaNegocioException("Correo Argentino no está configurado en esta tienda");
			}
			if (!datos.isCorreoDomicilio() && !datos.isCorreoSucursal()) {
				throw new ReglaNegocioException("Elegí al menos envío a domicilio o retiro en sucursal");
			}
			// El precio es el respaldo si el Correo no responde; sin precio, el costo queda a convenir
			zona.setCostoAConvenir(false);
			zona.setPrecio(datos.getPrecio());
		} else {
			zona.setCostoAConvenir(datos.isCostoAConvenir());
			if (datos.isCostoAConvenir()) {
				zona.setPrecio(null);
			} else if (datos.getPrecio() == null) {
				throw new ReglaNegocioException("Falta el precio del envío (o marcá que el costo es a convenir)");
			} else {
				zona.setPrecio(datos.getPrecio());
			}
		}
		zona.setCorreoDomicilio(datos.isCorreoDomicilio());
		zona.setCorreoSucursal(datos.isCorreoSucursal());
		zona.setCorreoExpreso(datos.isCorreoExpreso());
		if (datos.getDiasMin() != null && datos.getDiasMax() != null && datos.getDiasMin() > datos.getDiasMax()) {
			throw new ReglaNegocioException("El plazo mínimo no puede ser mayor que el máximo");
		}
		zona.setDiasMin(datos.getDiasMin());
		zona.setDiasMax(datos.getDiasMax());
		zona.setActiva(datos.isActiva());
		zona.setOrden(datos.getOrden());
	}

	// ─── Códigos postales ───────────────────────────────────────────────

	/** Los 4 dígitos del código postal ("3164", "A3164ABC" → "3164"). */
	static String normalizar(String codigoPostal) {
		Matcher m = CUATRO_DIGITOS.matcher(codigoPostal == null ? "" : codigoPostal);
		if (!m.find()) {
			throw new ReglaNegocioException("Código postal inválido: ingresá los 4 números (ej. 3164)");
		}
		return m.group(1);
	}

	/** Valida y deja prolija una lista como "3164,3100 - 3299" → "3164, 3100-3299". Vacía = resto del país. */
	static String normalizarLista(String lista) {
		if (!StringUtils.hasText(lista)) {
			return null;
		}
		List<String> tramos = new ArrayList<>();
		for (String parte : lista.split(",")) {
			String tramo = parte.trim();
			if (tramo.isEmpty()) {
				continue;
			}
			Matcher m = TRAMO.matcher(tramo);
			if (!m.matches()) {
				throw new ReglaNegocioException("Código postal inválido: \"" + tramo
						+ "\". Usá códigos de 4 números o rangos, ej. 3164, 3100-3299");
			}
			if (m.group(2) != null && Integer.parseInt(m.group(1)) > Integer.parseInt(m.group(2))) {
				throw new ReglaNegocioException("Rango invertido: \"" + tramo + "\"");
			}
			tramos.add(m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2));
		}
		return tramos.isEmpty() ? null : String.join(", ", tramos);
	}

	static boolean incluye(String lista, int codigo) {
		for (String tramo : lista.split(",")) {
			String[] limites = tramo.trim().split("-");
			int desde = Integer.parseInt(limites[0].trim());
			int hasta = limites.length > 1 ? Integer.parseInt(limites[1].trim()) : desde;
			if (codigo >= desde && codigo <= hasta) {
				return true;
			}
		}
		return false;
	}

	static String plazo(Integer min, Integer max) {
		if (min == null && max == null) {
			return null;
		}
		if (min == null || max == null || min.equals(max)) {
			int dias = min != null ? min : max;
			return dias == 0 ? "en el día" : dias + (dias == 1 ? " día hábil" : " días hábiles");
		}
		return min + " a " + max + " días hábiles";
	}
}
