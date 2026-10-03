package com.tiendas.catalogo;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tiendas.archivos.ImagenRepository;
import com.tiendas.comun.ConflictoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.config.CacheConfig;

@Service
@Transactional(readOnly = true)
public class ProductoService {

	private static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "precio", "creadoEn");
	private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "id");
	private static final int MAX_ALEATORIOS = 48;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] ALFABETO_SKU = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final ImagenRepository imagenRepository;

	public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
			ImagenRepository imagenRepository) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.imagenRepository = imagenRepository;
	}

	// ─── Lectura ────────────────────────────────────────────────────────

	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'productos:' + #filtro + ':' + #incluirInactivos + ':' + #pagina")
	public Page<Producto> buscar(FiltroProductos filtro, boolean incluirInactivos, Pageable pagina) {
		Pageable paginaSegura = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), validarOrden(pagina.getSort()));
		Page<Producto> productos = productoRepository.findAll(filtro.comoSpecification(incluirInactivos), paginaSegura);
		productos.forEach(ProductoService::inicializar);
		return productos;
	}

	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'producto:' + #id + ':' + #incluirInactivo")
	public Producto obtener(Long id, boolean incluirInactivo) {
		Producto producto = productoRepository.findById(id)
				.filter(p -> incluirInactivo || p.isActivo())
				.orElseThrow(() -> NoEncontradoException.de("Producto", id));
		return inicializar(producto);
	}

	/** Selección aleatoria para la home. Se cachea: el surtido cambia cada 10 minutos o al modificar el catálogo. */
	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'aleatorios:' + #cantidad")
	public List<Producto> aleatorios(int cantidad) {
		List<Long> ids = productoRepository.idsAleatorios(Math.clamp(cantidad, 1, MAX_ALEATORIOS));
		Map<Long, Producto> porId = productoRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Producto::getId, Function.identity()));
		return ids.stream().map(porId::get).filter(Objects::nonNull).map(ProductoService::inicializar).toList();
	}

	// ─── Escritura ──────────────────────────────────────────────────────

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Producto crear(Producto datos) {
		Producto producto = new Producto();
		copiarDatos(datos, producto);
		sincronizarVariantes(producto, datos.getVariantes());
		productoRepository.saveAndFlush(producto);
		return producto;
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public Producto actualizar(Long id, Producto datos) {
		Producto producto = productoRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Producto", id));
		verificarVersion(datos.getVersion(), producto.getVersion(), "El producto");
		copiarDatos(datos, producto);
		sincronizarVariantes(producto, datos.getVariantes());
		try {
			productoRepository.flush(); // para devolver versiones e ids nuevos actualizados
		} catch (DataIntegrityViolationException e) {
			throw new ConflictoException("No se puede quitar una variante que tiene pedidos: desactivala en su lugar");
		}
		return producto;
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public void eliminar(Long id) {
		productoRepository.delete(productoRepository.findById(id).orElseThrow(() -> NoEncontradoException.de("Producto", id)));
		try {
			productoRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictoException("El producto tiene pedidos asociados: desactivalo en lugar de eliminarlo");
		}
	}

	private void copiarDatos(Producto datos, Producto producto) {
		if (!categoriaRepository.existsById(datos.getCategoriaId())) {
			throw new ReglaNegocioException("La categoría " + datos.getCategoriaId() + " no existe");
		}
		validarImagenes(datos);
		producto.setCategoriaId(datos.getCategoriaId());
		producto.setNombre(datos.getNombre().trim());
		producto.setDescripcion(datos.getDescripcion());
		producto.setPrecio(datos.getPrecio());
		producto.setUnidadVenta(datos.getUnidadVenta());
		producto.setActivo(datos.isActivo());
		producto.setDestacado(datos.isDestacado());
		producto.getImagenes().clear();
		producto.getImagenes().addAll(datos.getImagenes());
	}

	/**
	 * Aplica la lista de variantes recibida: las que traen id se actualizan, las nuevas se crean
	 * y las que no vienen se eliminan.
	 */
	private void sincronizarVariantes(Producto producto, List<Variante> recibidas) {
		Map<Long, Variante> existentes = producto.getVariantes().stream()
				.collect(Collectors.toMap(Variante::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
		List<Variante> resultado = new ArrayList<>();

		for (Variante datos : recibidas) {
			Variante variante;
			if (datos.getId() != null) {
				variante = existentes.remove(datos.getId());
				if (variante == null) {
					throw new ReglaNegocioException("La variante " + datos.getId() + " no pertenece al producto");
				}
				verificarVersion(datos.getVersion(), variante.getVersion(), "La variante " + datos.getId());
			} else {
				variante = new Variante();
				variante.setProducto(producto);
			}
			copiarVariante(datos, variante, producto.getUnidadVenta());
			variante.setPosicion(resultado.size());
			resultado.add(variante);
		}

		// Se reemplaza el contenido (no la lista) para que orphanRemoval borre las variantes quitadas
		producto.getVariantes().clear();
		producto.getVariantes().addAll(resultado);
	}

	private static void copiarVariante(Variante datos, Variante variante, UnidadVenta unidad) {
		if (!unidad.admiteDecimales() && tieneDecimales(datos.getStock())) {
			throw new ReglaNegocioException("El stock debe ser un número entero para productos vendidos por unidad");
		}
		datos.getAtributos().forEach((clave, valor) -> {
			if (clave.isBlank() || clave.length() > 40 || valor == null || valor.length() > 100) {
				throw new ReglaNegocioException("Atributo inválido: cada nombre hasta 40 caracteres y cada valor hasta 100");
			}
		});
		variante.setSku(StringUtils.hasText(datos.getSku()) ? datos.getSku().trim() : generarSku());
		variante.setAtributos(new LinkedHashMap<>(datos.getAtributos()));
		variante.setPrecio(datos.getPrecio());
		variante.setStock(datos.getStock());
		variante.setImagenId(datos.getImagenId());
		variante.setActivo(datos.isActivo());
	}

	private void validarImagenes(Producto datos) {
		Set<UUID> ids = new HashSet<>(datos.getImagenes());
		datos.getVariantes().stream().map(Variante::getImagenId).filter(Objects::nonNull).forEach(ids::add);
		if (!ids.isEmpty() && imagenRepository.findAllById(ids).size() != ids.size()) {
			throw new ReglaNegocioException("Alguna de las imágenes indicadas no existe");
		}
	}

	private static void verificarVersion(Long recibida, Long actual, String recurso) {
		if (recibida != null && !recibida.equals(actual)) {
			throw new ConflictoException(recurso + " fue modificado por otra operación (por ejemplo, una venta). "
					+ "Volvé a cargarlo y reintentá.");
		}
	}

	private static Sort validarOrden(Sort orden) {
		if (orden.isUnsorted()) {
			return ORDEN_POR_DEFECTO;
		}
		for (Sort.Order o : orden) {
			if (!ORDENES_PERMITIDOS.contains(o.getProperty())) {
				throw new ReglaNegocioException("No se puede ordenar por " + o.getProperty()
						+ ". Opciones: " + ORDENES_PERMITIDOS);
			}
		}
		return orden.and(ORDEN_POR_DEFECTO); // desempate estable para que la paginación no repita productos
	}

	private static boolean tieneDecimales(BigDecimal valor) {
		return valor.stripTrailingZeros().scale() > 0;
	}

	private static String generarSku() {
		char[] sku = new char[10];
		for (int i = 0; i < sku.length; i++) {
			sku[i] = ALFABETO_SKU[RANDOM.nextInt(ALFABETO_SKU.length)];
		}
		return new String(sku);
	}

	/** Carga las colecciones LAZY dentro de la transacción (en lote para todos los productos de la página). */
	private static Producto inicializar(Producto producto) {
		Hibernate.initialize(producto.getVariantes());
		Hibernate.initialize(producto.getImagenes());
		return producto;
	}
}
