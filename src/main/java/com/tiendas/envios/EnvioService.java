package com.tiendas.envios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
import com.tiendas.correo.Provincias;
import com.tiendas.zipnova.Zipnova;

/**
 * Opciones de entrega para un código postal: retiro en el local y envío según la zona. Una zona tiene precio fijo
 * o se cotiza con Correo Argentino o con Zipnova (a domicilio y a sucursal); si no responden o faltan medidas, se
 * usa el precio de respaldo de la zona o el costo queda a convenir, para no perder la venta.
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
	private static final String PREFIJO_ZIPNOVA = "ZIPNOVA-";
	/** Largo máximo del id de una opción (columna {@code pedido.envio_opcion}). */
	private static final int LARGO_ID_OPCION = 40;
	private static final Pattern CUATRO_DIGITOS = Pattern.compile("(\\d{4})");
	private static final Pattern TRAMO = Pattern.compile("(\\d{4})(?:\\s*-\\s*(\\d{4}))?");

	/** Cómo recibe el cliente la compra. */
	public enum Modalidad {
		RETIRO, DOMICILIO, SUCURSAL
	}

	/**
	 * Una forma de recibir la compra. {@code precio} ya tiene aplicado el envío gratis por monto; es cero si
	 * el costo es a convenir (no se cobra en la web). {@code titulo} es el texto para el cliente, ej.
	 * "Envío a domicilio · Correo Argentino Clásico". En las opciones de Zipnova, {@code transportista} y
	 * {@code logo} dicen quién lleva el paquete, {@code entregaEstimada} es la fecha estimada (ISO),
	 * {@code destino} es la localidad tal como la entendió Zipnova, {@code puntos} son las sucursales para retirar
	 * (solo SUCURSAL) y {@code logistica} la forma de despacho (se guarda en el pedido, no se manda al front).
	 */
	public record OpcionEnvio(String id, Modalidad modalidad, boolean retiro, String titulo, String nombre,
			BigDecimal precio, boolean costoAConvenir, boolean gratisPorMonto, Integer diasMin, Integer diasMax,
			String detalle, String transportista, String logo, String entregaEstimada, String destino,
			List<Zipnova.Punto> puntos, @com.fasterxml.jackson.annotation.JsonIgnore String logistica) {

		/** Texto que queda guardado en el pedido, ej. "Envío a domicilio · Entre Ríos (2 a 4 días hábiles)". */
		public String descripcion() {
			String plazo = plazo(diasMin, diasMax);
			return plazo == null ? titulo : titulo + " (" + plazo + ")";
		}
	}

	/** La opción elegida en el checkout y, si es retiro en sucursal, la sucursal (código y texto para el pedido). */
	public record EnvioElegido(OpcionEnvio opcion, String sucursalCodigo, String sucursalDetalle) {
	}

	/**
	 * Adónde va la compra. {@code provincia} es el nombre o el código de una letra; {@code ciudad} es opcional (la
	 * usa Zipnova, que identifica la localidad por nombre).
	 */
	public record Destino(String codigoPostal, String provincia, String ciudad) {

		static final Destino NINGUNO = new Destino(null, null, null);
	}

	/** {@code destino}: la localidad tal como la entendió Zipnova (ej. "Reconquista, Santa Fe"), si cotizó Zipnova. */
	public record Cotizacion(String codigoPostal, String provincia, BigDecimal subtotal, BigDecimal envioGratisDesde,
			String destino, List<OpcionEnvio> opciones) {
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
	private final ObjectProvider<Zipnova> zipnova;

	public EnvioService(ZonaEnvioRepository zonaRepository, AjustesEnvioRepository ajustesRepository,
			VarianteRepository varianteRepository, CategoriaRepository categoriaRepository, EnviosProperties envios,
			ObjectProvider<CorreoArgentino> correo, ObjectProvider<Zipnova> zipnova) {
		this.zonaRepository = zonaRepository;
		this.ajustesRepository = ajustesRepository;
		this.varianteRepository = varianteRepository;
		this.categoriaRepository = categoriaRepository;
		this.envios = envios;
		this.correo = correo;
		this.zipnova = zipnova;
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

	/** Opciones para un carrito (variante → cantidad). Sin código postal ni provincia, solo el retiro. */
	public Cotizacion cotizar(Destino destino, Map<Long, BigDecimal> cantidades) {
		BigDecimal subtotal = varianteRepository.buscarConProducto(cantidades.keySet()).stream()
				.filter(v -> v.isActivo() && v.getProducto().isActivo())
				.map(v -> precioDe(v, cantidades.get(v.getId())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		List<OpcionEnvio> opciones = opciones(destino, cantidades, subtotal);
		return new Cotizacion(StringUtils.hasText(destino.codigoPostal()) ? normalizar(destino.codigoPostal()) : null,
				Provincias.codigo(destino.provincia()), subtotal, ajustes().getEnvioGratisDesde(),
				opciones.stream().map(OpcionEnvio::destino).filter(StringUtils::hasText).findFirst().orElse(null),
				opciones);
	}

	/** Elige la zona por código postal y, si no coincide con ninguna, por provincia (nombre o código de una letra). */
	public List<OpcionEnvio> opciones(Destino destino, Map<Long, BigDecimal> cantidades, BigDecimal subtotal) {
		AjustesEnvio ajustes = ajustes();
		List<OpcionEnvio> opciones = new ArrayList<>();
		if (ajustes.isRetiroHabilitado()) {
			opciones.add(new OpcionEnvio(RETIRO, Modalidad.RETIRO, true, "Retiro en el local", "Retiro en el local",
					BigDecimal.ZERO, false, false, null, null, ajustes.getRetiroDetalle(), null, null, null, null,
					List.of(), null));
		}
		String cp = StringUtils.hasText(destino.codigoPostal()) ? normalizar(destino.codigoPostal()) : null;
		String codigoProvincia = Provincias.codigo(destino.provincia());
		if (cp != null || codigoProvincia != null) {
			zonaPara(cp, codigoProvincia).ifPresent(zona -> opciones.addAll(switch (zona.getTipo()) {
				case PRECIO_FIJO -> List.of(opcionDe(zona, subtotal, ajustes));
				// El Correo cotiza por código postal: sin él, el precio de respaldo de la zona
				case CORREO_ARGENTINO -> cp == null ? List.of(respaldo(zona, subtotal, ajustes))
						: opcionesCorreo(zona, cp, cantidades, subtotal, ajustes);
				// Zipnova identifica el destino por localidad + provincia (sin localidad rechaza la cotización)
				case ZIPNOVA -> cp == null || codigoProvincia == null || !StringUtils.hasText(destino.ciudad())
						? sinCotizacion(zona, subtotal, ajustes)
						: opcionesZipnova(zona, new Zipnova.Destino(cp, destino.ciudad(), Provincias.nombre(codigoProvincia)),
								cantidades, subtotal, ajustes);
			}));
		}
		return opciones;
	}

	/**
	 * Valida la entrega elegida en el checkout y devuelve la opción con su precio.
	 *
	 * @param opcionId       opción que eligió el cliente; si es nula se toma la primera disponible
	 * @param sucursalCodigo sucursal donde retira (solo modalidad SUCURSAL): la del Correo define el precio con su
	 *                       código postal; la de Zipnova tiene que ser una de las que trae la opción elegida
	 */
	public EnvioElegido elegir(Modalidad modalidad, Destino destino, String opcionId, String sucursalCodigo,
			BigDecimal subtotal, Map<Long, BigDecimal> cantidades) {
		if (modalidad == Modalidad.RETIRO) {
			if (!ajustes().isRetiroHabilitado()) {
				throw new ReglaNegocioException("La tienda no permite retirar los pedidos");
			}
			return new EnvioElegido(opciones(Destino.NINGUNO, cantidades, subtotal).getFirst(), null, null);
		}
		if (modalidad == Modalidad.SUCURSAL && opcionId != null && opcionId.startsWith(PREFIJO_ZIPNOVA)) {
			return elegirPuntoZipnova(destino, opcionId, sucursalCodigo, subtotal, cantidades);
		}

		CorreoArgentino.Sucursal sucursal = null;
		String cp = StringUtils.hasText(destino.codigoPostal()) ? normalizar(destino.codigoPostal()) : null;
		String codigoProvincia = Provincias.codigo(destino.provincia());
		if (modalidad == Modalidad.SUCURSAL) {
			sucursal = sucursal(sucursalCodigo);
			codigoProvincia = sucursal.codigo().substring(0, 1);
			if (sucursal.codigoPostal() != null) {
				cp = normalizar(sucursal.codigoPostal());
			}
		}
		if (cp == null && codigoProvincia == null) {
			throw new ReglaNegocioException("Falta el código postal para calcular el envío");
		}
		String lugar = cp != null ? "el código postal " + cp : Provincias.nombre(codigoProvincia);
		OpcionEnvio opcion = opciones(new Destino(cp, codigoProvincia, destino.ciudad()), cantidades, subtotal).stream()
				.filter(o -> o.modalidad() == modalidad)
				.filter(o -> opcionId == null || o.id().equals(opcionId))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException(opcionId == null
						? "No hay envíos " + (modalidad == Modalidad.SUCURSAL ? "a sucursal" : "a domicilio")
								+ " para " + lugar + ". Consultanos por WhatsApp."
						: "La opción de envío elegida ya no está disponible para " + lugar));
		return sucursal == null ? new EnvioElegido(opcion, null, null)
				: new EnvioElegido(opcion, sucursal.codigo(), sucursal.detalle());
	}

	/**
	 * Retiro en una sucursal de Zipnova: se vuelve a cotizar para el destino del cliente y la sucursal tiene que
	 * ser una de las que trae la opción elegida (Zipnova ofrece las más cercanas al destino).
	 */
	private EnvioElegido elegirPuntoZipnova(Destino destino, String opcionId, String puntoId, BigDecimal subtotal,
			Map<Long, BigDecimal> cantidades) {
		if (!StringUtils.hasText(puntoId)) {
			throw new ReglaNegocioException("Elegí la sucursal donde vas a retirar");
		}
		if (!StringUtils.hasText(destino.codigoPostal()) || Provincias.codigo(destino.provincia()) == null) {
			throw new ReglaNegocioException("Falta tu provincia y tu código postal para calcular el envío");
		}
		OpcionEnvio opcion = opciones(destino, cantidades, subtotal).stream()
				.filter(o -> o.id().equals(opcionId))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException(
						"La opción de envío elegida ya no está disponible: elegí otra"));
		Zipnova.Punto punto = opcion.puntos().stream()
				.filter(p -> p.id().equals(puntoId.trim()))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException("La sucursal elegida ya no está disponible: elegí otra"));
		return new EnvioElegido(opcion, punto.id(), punto.detalle());
	}

	/**
	 * Separa el id de una opción de Zipnova ("ZIPNOVA-{zona}-{transporte}-{servicio}") en el transporte y el código
	 * del servicio, que son los datos que pide Zipnova para crear el envío. Vacío si no es una opción de Zipnova.
	 */
	public static Optional<ServicioZipnova> servicioZipnova(String opcionId) {
		if (opcionId == null || !opcionId.startsWith(PREFIJO_ZIPNOVA)) {
			return Optional.empty();
		}
		String[] partes = opcionId.substring(PREFIJO_ZIPNOVA.length()).split("-", 3);
		if (partes.length < 3) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ServicioZipnova(Long.parseLong(partes[1]), partes[2]));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public record ServicioZipnova(long transportistaId, String servicio) {
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
	 * Bultos del carrito para cotizar con el Correo: peso y medidas de cada producto, de su categoría o, si no
	 * tiene, el paquete por defecto de la tienda. Vacío si a algún producto le faltan datos y no hay paquete por
	 * defecto, porque cotizar sin ellos daría un precio equivocado.
	 */
	public Optional<List<CorreoArgentino.Paquete>> paquetes(Map<Long, BigDecimal> cantidades) {
		Medidas paquete = ajustes().getPaquetePorDefecto();
		Medidas porDefecto = completas(paquete) ? paquete : null;
		if (cantidades.isEmpty()) {
			// El probador del panel cotiza sin carrito: un paquete por defecto
			return Optional.ofNullable(porDefecto).map(m -> List.of(new CorreoArgentino.Paquete(1, m.getPesoGramos(),
					m.getLargoCm(), m.getAnchoCm(), m.getAltoCm())));
		}
		// Los servicios no viajan: no suman bultos
		List<Variante> variantes = varianteRepository.buscarConProducto(cantidades.keySet()).stream()
				.filter(v -> !v.getProducto().isServicio())
				.toList();
		Map<Long, Medidas> porCategoria = categoriaRepository.findAllById(variantes.stream()
				.map(v -> v.getProducto().getCategoriaId()).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
				.filter(c -> completas(c.getMedidas()))
				.collect(Collectors.toMap(Categoria::getId, Categoria::getMedidas));
		List<CorreoArgentino.Paquete> paquetes = new ArrayList<>();
		for (Variante variante : variantes) {
			Medidas medidas = completas(variante.getProducto().getMedidas()) ? variante.getProducto().getMedidas()
					: porCategoria.getOrDefault(variante.getProducto().getCategoriaId(), porDefecto);
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
							t.diasMin(), t.diasMax(), null, "Correo Argentino", null, null, null, List.of(), null);
				})
				.toList();
		return opciones.isEmpty() ? List.of(respaldo(zona, subtotal, ajustes)) : opciones;
	}

	/**
	 * Las opciones de Zipnova para el destino: la mejor de cada forma de entrega (domicilio, retiro en sucursal y,
	 * si la zona lo permite, express), filtradas por los servicios que ofrece la zona.
	 */
	private List<OpcionEnvio> opcionesZipnova(ZonaEnvio zona, Zipnova.Destino destino, Map<Long, BigDecimal> cantidades,
			BigDecimal subtotal, AjustesEnvio ajustes) {
		Zipnova cliente = zipnova.getIfAvailable();
		Optional<List<Zipnova.Item>> items = items(cantidades);
		if (cliente == null || items.isEmpty()) {
			log.warn("Zona {}: no se puede cotizar con Zipnova ({})", zona.getNombre(),
					cliente == null ? "Zipnova no está habilitado" : "faltan peso y medidas de algún producto y no hay "
							+ "paquete por defecto");
			return sinCotizacion(zona, subtotal, ajustes);
		}
		Zipnova.Cotizacion cotizacion;
		try {
			cotizacion = cliente.cotizar(destino, items.get(), subtotal);
		} catch (RuntimeException e) {
			log.warn("Zipnova no cotizó el envío a {} ({}): {}", destino.codigoPostal(), zona.getNombre(), e.getMessage());
			return sinCotizacion(zona, subtotal, ajustes);
		}
		boolean gratis = gratisPorMonto(subtotal, ajustes);
		List<OpcionEnvio> opciones = cotizacion.opciones().stream()
				.filter(o -> o.retiroEnPunto() ? zona.isCorreoSucursal() : zona.isCorreoDomicilio())
				.filter(o -> !o.urgente() || zona.isCorreoExpreso())
				// Primero las de domicilio y después las de sucursal, cada grupo de la más barata a la más cara
				.sorted(Comparator.comparing(Zipnova.Opcion::retiroEnPunto).thenComparing(Zipnova.Opcion::precio))
				.map(o -> {
					String nombre = o.transportista() + (o.urgente() ? " Express" : "");
					return new OpcionEnvio(PREFIJO_ZIPNOVA + zona.getId() + "-" + o.transportistaId() + "-" + o.servicio(),
							o.retiroEnPunto() ? Modalidad.SUCURSAL : Modalidad.DOMICILIO, false,
							(o.retiroEnPunto() ? "Retiro en sucursal · " : "Envío a domicilio · ") + nombre, nombre,
							gratis ? BigDecimal.ZERO : o.precio(), false, gratis, o.diasMin(), o.diasMax(), null,
							o.transportista(), o.logo(), o.entregaEstimada(), cotizacion.destino(), o.puntos(),
							o.logistica());
				})
				.filter(o -> {
					// El id viaja en el pedido (columna de 40): uno más largo no se podría guardar
					if (o.id().length() <= LARGO_ID_OPCION) {
						return true;
					}
					log.warn("Opción de Zipnova descartada por id demasiado largo: {}", o.id());
					return false;
				})
				.toList();
		return opciones.isEmpty() ? sinCotizacion(zona, subtotal, ajustes) : opciones;
	}

	/**
	 * Zona de Zipnova que no se pudo cotizar: el precio de respaldo si el dueño cargó uno; si no, no se ofrece envío
	 * (nunca "a convenir": el envío lo cobra la web con el precio de Zipnova).
	 */
	private static List<OpcionEnvio> sinCotizacion(ZonaEnvio zona, BigDecimal subtotal, AjustesEnvio ajustes) {
		return zona.getPrecio() == null ? List.of() : List.of(respaldo(zona, subtotal, ajustes));
	}

	/**
	 * Unidades del carrito para cotizar con Zipnova (que arma los paquetes), con el peso y las medidas de cada
	 * producto o de su categoría. Vacío si a alguno le faltan datos.
	 */
	public Optional<List<Zipnova.Item>> items(Map<Long, BigDecimal> cantidades) {
		return paquetes(cantidades).map(paquetes -> paquetes.stream()
				.flatMap(p -> java.util.stream.Stream.generate(() -> new Zipnova.Item(p.pesoGramos(), p.largoCm(),
						p.anchoCm(), p.altoCm(), null)).limit(p.cantidad()))
				.toList());
	}

	/** Si el Correo o Zipnova no pueden cotizar: el precio fijo de la zona o, si no tiene, costo a convenir. */
	private static OpcionEnvio respaldo(ZonaEnvio zona, BigDecimal subtotal, AjustesEnvio ajustes) {
		boolean aConvenir = zona.getPrecio() == null;
		boolean gratis = !aConvenir && gratisPorMonto(subtotal, ajustes);
		return new OpcionEnvio(PREFIJO_ZONA + zona.getId(), Modalidad.DOMICILIO, false,
				"Envío a domicilio · " + zona.getNombre(), zona.getNombre(),
				aConvenir || gratis ? BigDecimal.ZERO : zona.getPrecio(), aConvenir, gratis, zona.getDiasMin(),
				zona.getDiasMax(), null, null, null, null, null, List.of(), null);
	}

	/**
	 * Primero las zonas por código postal (lo local), después las zonas por provincia y al final la zona sin
	 * códigos ni provincias ("resto del país"). Dentro de cada grupo, por orden.
	 */
	private Optional<ZonaEnvio> zonaPara(String cp, String codigoProvincia) {
		List<ZonaEnvio> activas = zonaRepository.findByActivaTrueOrderByOrdenAscIdAsc();
		Optional<ZonaEnvio> porCodigo = cp == null ? Optional.empty() : activas.stream()
				.filter(z -> StringUtils.hasText(z.getCodigosPostales()))
				.filter(z -> incluye(z.getCodigosPostales(), Integer.parseInt(cp)))
				.findFirst();
		return porCodigo
				.or(() -> codigoProvincia == null ? Optional.empty() : activas.stream()
						.filter(z -> !StringUtils.hasText(z.getCodigosPostales()) && StringUtils.hasText(z.getProvincias()))
						.filter(z -> List.of(z.getProvincias().split(",")).contains(codigoProvincia))
						.findFirst())
				.or(() -> activas.stream()
						.filter(z -> !StringUtils.hasText(z.getCodigosPostales()) && !StringUtils.hasText(z.getProvincias()))
						.findFirst());
	}

	private static OpcionEnvio opcionDe(ZonaEnvio zona, BigDecimal subtotal, AjustesEnvio ajustes) {
		boolean gratis = !zona.isCostoAConvenir() && gratisPorMonto(subtotal, ajustes);
		BigDecimal precio = zona.isCostoAConvenir() || gratis ? BigDecimal.ZERO : zona.getPrecio();
		return new OpcionEnvio(PREFIJO_ZONA + zona.getId(), Modalidad.DOMICILIO, false,
				"Envío a domicilio · " + zona.getNombre(), zona.getNombre(), precio, zona.isCostoAConvenir(), gratis,
				zona.getDiasMin(), zona.getDiasMax(), null, null, null, null, null, List.of(), null);
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
		Medidas paquete = Medidas.copiar(datos.getPaquetePorDefecto());
		if (paquete != null && !completas(paquete)) {
			throw new ReglaNegocioException("Completá peso, largo, ancho y alto del paquete por defecto (o dejalo vacío)");
		}
		ajustes.setPaquetePorDefecto(paquete);
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
		zona.setProvincias(normalizarProvincias(datos.getProvincias()));
		zona.setTipo(datos.getTipo() == null ? TipoZona.PRECIO_FIJO : datos.getTipo());
		if (zona.getTipo() != TipoZona.PRECIO_FIJO) {
			if (zona.getTipo() == TipoZona.CORREO_ARGENTINO && correo.getIfAvailable() == null) {
				throw new ReglaNegocioException("Correo Argentino no está configurado en esta tienda");
			}
			if (zona.getTipo() == TipoZona.ZIPNOVA && zipnova.getIfAvailable() == null) {
				throw new ReglaNegocioException("Zipnova no está configurado en esta tienda");
			}
			if (!datos.isCorreoDomicilio() && !datos.isCorreoSucursal()) {
				throw new ReglaNegocioException("Elegí al menos envío a domicilio o retiro en sucursal");
			}
			// El precio es el respaldo si el Correo o Zipnova no responden; sin precio, el costo queda a convenir
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

	/** "Entre Ríos, s ,X" → "E,S,X" (códigos de una letra, sin repetir, en orden). Vacía = sin provincias. */
	static String normalizarProvincias(String lista) {
		if (!StringUtils.hasText(lista)) {
			return null;
		}
		Set<String> codigos = new TreeSet<>();
		for (String parte : lista.split(",")) {
			if (!StringUtils.hasText(parte)) {
				continue;
			}
			String codigo = Provincias.codigo(parte);
			if (codigo == null) {
				throw new ReglaNegocioException("Provincia desconocida: \"" + parte.trim() + "\"");
			}
			codigos.add(codigo);
		}
		return codigos.isEmpty() ? null : String.join(",", codigos);
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
