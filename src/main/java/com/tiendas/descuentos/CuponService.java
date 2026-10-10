package com.tiendas.descuentos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.tiendas.catalogo.Categoria;
import com.tiendas.catalogo.CategoriaRepository;
import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;

/**
 * Administración de cupones y cálculo del descuento de una compra. El checkout llama a
 * {@link #aplicar}: todas las condiciones del cupón se validan acá, del lado del servidor.
 */
@Service
@Transactional(readOnly = true)
public class CuponService {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	/** Producto de una línea del carrito, con lo necesario para saber si el cupón lo alcanza. */
	public record LineaCompra(Long productoId, Long categoriaId, BigDecimal subtotal) {
	}

	/** Cupón válido para la compra y cuánto descuenta (en pesos, ya redondeado). */
	public record CuponAplicado(Cupon cupon, BigDecimal descuento) {
	}

	private final CuponRepository cuponRepository;
	private final CategoriaRepository categoriaRepository;
	private final LimitadorIntentosCupon limitador;
	private final DescuentosProperties descuentos;

	public CuponService(CuponRepository cuponRepository, CategoriaRepository categoriaRepository,
			LimitadorIntentosCupon limitador, DescuentosProperties descuentos) {
		this.cuponRepository = cuponRepository;
		this.categoriaRepository = categoriaRepository;
		this.limitador = limitador;
		this.descuentos = descuentos;
	}

	// ─── Checkout ───────────────────────────────────────────────────────

	/**
	 * Valida el cupón para esta compra y calcula el descuento. Lanza {@link ReglaNegocioException} con un
	 * mensaje para el cliente si el cupón no se puede usar.
	 *
	 * @param email      email del comprador; si es nulo (el cliente todavía no lo cargó) no se validan las
	 *                   condiciones por cliente, que se vuelven a validar al confirmar la compra
	 * @param conEnvio   si la compra es con envío a domicilio
	 * @param costoEnvio costo de envío que cobra la web (puede ser cero si el envío se coordina aparte)
	 */
	public CuponAplicado aplicar(String codigoIngresado, String email, List<LineaCompra> lineas, boolean conEnvio,
			BigDecimal costoEnvio, String ip) {
		if (!descuentos.habilitada()) {
			throw new ReglaNegocioException("Esta tienda no tiene cupones de descuento");
		}
		if (limitador.estaBloqueado(ip)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Probaste demasiados códigos. Esperá unos minutos y volvé a intentar.");
		}
		Cupon cupon = cuponRepository.findByCodigo(normalizar(codigoIngresado)).filter(Cupon::isActivo).orElse(null);
		if (cupon == null) {
			limitador.registrarFallo(ip);
			throw new ReglaNegocioException("Ese código no existe o ya no está disponible");
		}

		Instant ahora = Instant.now();
		if (cupon.getDesde() != null && ahora.isBefore(cupon.getDesde())) {
			throw new ReglaNegocioException("El cupón todavía no está vigente");
		}
		if (cupon.getHasta() != null && !ahora.isBefore(cupon.getHasta())) {
			throw new ReglaNegocioException("El cupón ya venció");
		}
		if (cupon.getUsosMaximos() != null && cuponRepository.contarUsos(cupon.getId()) >= cupon.getUsosMaximos()) {
			throw new ReglaNegocioException("El cupón ya alcanzó su límite de usos");
		}
		if (StringUtils.hasText(email)) {
			if (cupon.isSoloPrimeraCompra() && cuponRepository.clienteYaCompro(email.trim())) {
				throw new ReglaNegocioException("El cupón es solo para la primera compra");
			}
			if (cupon.getUsosPorCliente() != null
					&& cuponRepository.contarUsosDeCliente(cupon.getId(), email.trim()) >= cupon.getUsosPorCliente()) {
				throw new ReglaNegocioException("Ya usaste este cupón la cantidad de veces permitida");
			}
		}

		BigDecimal base = baseAlcanzada(cupon, lineas);
		if (base.signum() <= 0) {
			throw new ReglaNegocioException("El cupón no aplica a los productos del carrito");
		}
		if (cupon.getMinimoCompra() != null && base.compareTo(cupon.getMinimoCompra()) < 0) {
			throw new ReglaNegocioException("El cupón es para compras desde $" + pesos(cupon.getMinimoCompra())
					+ (cupon.aplicaATodaLaTienda() ? "" : " en los productos de la promoción"));
		}

		BigDecimal descuento = switch (cupon.getTipo()) {
			case PORCENTAJE -> {
				BigDecimal calculado = base.multiply(cupon.getValor()).divide(CIEN, 2, RoundingMode.HALF_UP);
				yield cupon.getTope() == null ? calculado : calculado.min(cupon.getTope());
			}
			case MONTO_FIJO -> cupon.getValor().min(base);
			case ENVIO_GRATIS -> {
				if (!conEnvio) {
					throw new ReglaNegocioException("El cupón es de envío gratis: elegí envío a domicilio para usarlo");
				}
				// Si la web no cobra el envío (se coordina aparte, ej. por WhatsApp), el descuento es cero y el
				// pedido solo queda marcado con el cupón para que la tienda no cobre el envío al coordinarlo
				yield costoEnvio == null ? BigDecimal.ZERO : costoEnvio;
			}
		};
		// Así, un pedido con cupón y descuento cero siempre es de envío gratis
		if (descuento.signum() <= 0 && cupon.getTipo() != TipoCupon.ENVIO_GRATIS) {
			throw new ReglaNegocioException("El cupón no aplica a los productos del carrito");
		}
		return new CuponAplicado(cupon, descuento.setScale(2, RoundingMode.HALF_UP));
	}

	public TipoCupon tipoDe(Long cuponId) {
		return cuponId == null ? null : cuponRepository.findById(cuponId).map(Cupon::getTipo).orElse(null);
	}

	/** Suma de las líneas que el cupón alcanza (todas, si el cupón es para toda la tienda). */
	private BigDecimal baseAlcanzada(Cupon cupon, List<LineaCompra> lineas) {
		Map<Long, Long> rubroDeCategoria = cupon.getRubros().isEmpty() ? Map.of()
				: categoriaRepository.findAllById(lineas.stream().map(LineaCompra::categoriaId).collect(Collectors.toSet()))
						.stream().collect(Collectors.toMap(Categoria::getId, Categoria::getRubroId));
		return lineas.stream()
				.filter(linea -> cupon.aplicaATodaLaTienda()
						|| cupon.getProductos().contains(linea.productoId())
						|| cupon.getCategorias().contains(linea.categoriaId())
						|| cupon.getRubros().contains(rubroDeCategoria.get(linea.categoriaId())))
				.map(LineaCompra::subtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// ─── Panel de administración ────────────────────────────────────────

	public List<Cupon> listar() {
		List<Cupon> cupones = cuponRepository.findAllByOrderByCreadoEnDesc();
		Map<Long, CuponRepository.Metricas> metricas = cuponRepository.metricas().stream()
				.collect(Collectors.toMap(CuponRepository.Metricas::getCuponId, Function.identity()));
		cupones.forEach(cupon -> completar(cupon, metricas.get(cupon.getId())));
		return cupones;
	}

	public Cupon obtener(Long id) {
		Cupon cupon = buscar(id);
		completar(cupon, cuponRepository.metricas().stream()
				.filter(m -> m.getCuponId().equals(id)).findFirst().orElse(null));
		return cupon;
	}

	@Transactional
	public Cupon crear(Cupon datos) {
		Cupon cupon = new Cupon();
		copiar(datos, cupon);
		if (cuponRepository.existsByCodigo(cupon.getCodigo())) {
			throw new ConflictoException("Ya existe un cupón con el código " + cupon.getCodigo());
		}
		return cuponRepository.save(cupon);
	}

	@Transactional
	public Cupon actualizar(Long id, Cupon datos) {
		Cupon cupon = buscar(id);
		copiar(datos, cupon);
		if (cuponRepository.existsByCodigoAndIdNot(cupon.getCodigo(), id)) {
			throw new ConflictoException("Ya existe otro cupón con el código " + cupon.getCodigo());
		}
		cuponRepository.flush();
		return obtener(id);
	}

	/** Solo se pueden borrar cupones que nunca se usaron; los usados se desactivan para no perder sus métricas. */
	@Transactional
	public void eliminar(Long id) {
		Cupon cupon = buscar(id);
		if (cuponRepository.tienePedidos(id)) {
			throw new ConflictoException("El cupón ya se usó en pedidos: desactivalo en lugar de eliminarlo");
		}
		cuponRepository.delete(cupon);
	}

	private Cupon buscar(Long id) {
		return cuponRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Cupón", id));
	}

	private static void completar(Cupon cupon, CuponRepository.Metricas metricas) {
		Hibernate.initialize(cupon.getRubros());
		Hibernate.initialize(cupon.getCategorias());
		Hibernate.initialize(cupon.getProductos());
		if (metricas != null) {
			cupon.setUsos(metricas.getUsos());
			cupon.setTotalVendido(metricas.getTotalVendido());
			cupon.setTotalDescontado(metricas.getTotalDescontado());
		}
	}

	private static void copiar(Cupon datos, Cupon cupon) {
		cupon.setCodigo(normalizar(datos.getCodigo()));
		cupon.setDescripcion(StringUtils.hasText(datos.getDescripcion()) ? datos.getDescripcion().trim() : null);
		cupon.setTipo(datos.getTipo());
		switch (datos.getTipo()) {
			case PORCENTAJE -> {
				if (datos.getValor() == null || datos.getValor().compareTo(CIEN) > 0) {
					throw new ReglaNegocioException("El porcentaje tiene que estar entre 1 y 100");
				}
				cupon.setValor(datos.getValor());
				cupon.setTope(datos.getTope());
			}
			case MONTO_FIJO -> {
				if (datos.getValor() == null) {
					throw new ReglaNegocioException("Falta el monto a descontar");
				}
				cupon.setValor(datos.getValor());
				cupon.setTope(null);
			}
			case ENVIO_GRATIS -> {
				cupon.setValor(null);
				cupon.setTope(null);
			}
		}
		cupon.setMinimoCompra(datos.getMinimoCompra());
		if (datos.getDesde() != null && datos.getHasta() != null && !datos.getDesde().isBefore(datos.getHasta())) {
			throw new ReglaNegocioException("La fecha de fin tiene que ser posterior a la de inicio");
		}
		cupon.setDesde(datos.getDesde());
		cupon.setHasta(datos.getHasta());
		cupon.setUsosMaximos(datos.getUsosMaximos());
		cupon.setUsosPorCliente(datos.getUsosPorCliente());
		cupon.setSoloPrimeraCompra(datos.isSoloPrimeraCompra());
		cupon.setActivo(datos.isActivo());
		reemplazar(cupon.getRubros(), datos.getRubros());
		reemplazar(cupon.getCategorias(), datos.getCategorias());
		reemplazar(cupon.getProductos(), datos.getProductos());
	}

	private static void reemplazar(Set<Long> actuales, Collection<Long> nuevos) {
		actuales.clear();
		if (nuevos != null) {
			nuevos.stream().filter(Objects::nonNull).forEach(actuales::add);
		}
	}

	static String normalizar(String codigo) {
		return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
	}

	private static String pesos(BigDecimal monto) {
		DecimalFormat formato = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
		return formato.format(monto);
	}
}
