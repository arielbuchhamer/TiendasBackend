package com.tiendas.envios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tiendas.catalogo.Variante;
import com.tiendas.catalogo.VarianteRepository;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;

/**
 * Opciones de entrega para un código postal: retiro en el local y envío a domicilio según la zona.
 * El checkout llama a {@link #elegir} y el precio del envío siempre sale de acá, nunca del cliente.
 * <p>
 * Los códigos postales se comparan por sus 4 dígitos (también acepta el formato CPA, ej. "A3164ABC").
 */
@Service
@Transactional(readOnly = true)
public class EnvioService {

	public static final String RETIRO = "RETIRO";
	private static final String PREFIJO_ZONA = "ZONA-";
	private static final Pattern CUATRO_DIGITOS = Pattern.compile("(\\d{4})");
	private static final Pattern TRAMO = Pattern.compile("(\\d{4})(?:\\s*-\\s*(\\d{4}))?");

	/**
	 * Una forma de recibir la compra. {@code precio} ya tiene aplicado el envío gratis por monto; es cero si
	 * el costo es a convenir (no se cobra en la web).
	 */
	public record OpcionEnvio(String id, boolean retiro, String nombre, BigDecimal precio, boolean costoAConvenir,
			boolean gratisPorMonto, Integer diasMin, Integer diasMax, String detalle) {

		/** Texto que queda guardado en el pedido, ej. "Envío a domicilio: Entre Ríos (2 a 4 días hábiles)". */
		public String descripcion() {
			String base = retiro ? nombre : "Envío a domicilio: " + nombre;
			String plazo = plazo(diasMin, diasMax);
			return plazo == null ? base : base + " (" + plazo + ")";
		}
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
	private final EnviosProperties envios;

	public EnvioService(ZonaEnvioRepository zonaRepository, AjustesEnvioRepository ajustesRepository,
			VarianteRepository varianteRepository, EnviosProperties envios) {
		this.zonaRepository = zonaRepository;
		this.ajustesRepository = ajustesRepository;
		this.varianteRepository = varianteRepository;
		this.envios = envios;
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
				ajustes().getEnvioGratisDesde(), opciones(codigoPostal, subtotal));
	}

	public List<OpcionEnvio> opciones(String codigoPostal, BigDecimal subtotal) {
		AjustesEnvio ajustes = ajustes();
		List<OpcionEnvio> opciones = new ArrayList<>();
		if (ajustes.isRetiroHabilitado()) {
			opciones.add(new OpcionEnvio(RETIRO, true, "Retiro en el local", BigDecimal.ZERO, false, false, null, null,
					ajustes.getRetiroDetalle()));
		}
		if (StringUtils.hasText(codigoPostal)) {
			zonaPara(normalizar(codigoPostal)).ifPresent(zona -> opciones.add(opcionDe(zona, subtotal, ajustes)));
		}
		return opciones;
	}

	/**
	 * Valida la entrega elegida en el checkout y devuelve la opción con su precio.
	 *
	 * @param opcionId opción que eligió el cliente; si es nula se toma la única disponible
	 */
	public OpcionEnvio elegir(boolean domicilio, String codigoPostal, String opcionId, BigDecimal subtotal) {
		if (!domicilio) {
			if (!ajustes().isRetiroHabilitado()) {
				throw new ReglaNegocioException("La tienda no permite retirar los pedidos");
			}
			return opciones(null, subtotal).getFirst();
		}
		if (!StringUtils.hasText(codigoPostal)) {
			throw new ReglaNegocioException("Falta el código postal para calcular el envío");
		}
		String cp = normalizar(codigoPostal);
		return opciones(cp, subtotal).stream()
				.filter(o -> !o.retiro())
				.filter(o -> opcionId == null || o.id().equals(opcionId))
				.findFirst()
				.orElseThrow(() -> new ReglaNegocioException(opcionId == null
						? "No hay envíos a domicilio para el código postal " + cp + ". Consultanos por WhatsApp."
						: "La opción de envío elegida ya no está disponible para el código postal " + cp));
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
		boolean gratis = !zona.isCostoAConvenir() && ajustes.getEnvioGratisDesde() != null
				&& subtotal.compareTo(ajustes.getEnvioGratisDesde()) >= 0;
		BigDecimal precio = zona.isCostoAConvenir() || gratis ? BigDecimal.ZERO : zona.getPrecio();
		return new OpcionEnvio(PREFIJO_ZONA + zona.getId(), false, zona.getNombre(), precio, zona.isCostoAConvenir(),
				gratis, zona.getDiasMin(), zona.getDiasMax(), null);
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

	private static void copiar(ZonaEnvio datos, ZonaEnvio zona) {
		zona.setNombre(datos.getNombre().trim());
		zona.setCodigosPostales(normalizarLista(datos.getCodigosPostales()));
		zona.setCostoAConvenir(datos.isCostoAConvenir());
		if (datos.isCostoAConvenir()) {
			zona.setPrecio(null);
		} else if (datos.getPrecio() == null) {
			throw new ReglaNegocioException("Falta el precio del envío (o marcá que el costo es a convenir)");
		} else {
			zona.setPrecio(datos.getPrecio());
		}
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
