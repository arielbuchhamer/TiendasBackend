package com.tiendas.personalizacion;

import java.math.BigDecimal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tiendas.catalogo.Producto;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.config.CacheConfig;

/**
 * Personalización de productos (ej. grabado) con un precio único por unidad. Existe siempre porque el checkout
 * la consulta; con el módulo apagado rechaza cualquier producto personalizado.
 */
@Service
@Transactional(readOnly = true)
public class PersonalizacionService {

	/** Lo que el checkout copia en la línea del pedido. */
	public record Aplicada(String nombre, BigDecimal precio) {
	}

	private final AjustesPersonalizacionRepository repository;
	private final PersonalizacionProperties propiedades;

	PersonalizacionService(AjustesPersonalizacionRepository repository, PersonalizacionProperties propiedades) {
		this.repository = repository;
		this.propiedades = propiedades;
	}

	/** La página de cada producto la pide: va a la caché del catálogo. */
	@Cacheable(cacheNames = CacheConfig.CATALOGO, key = "'personalizacion'")
	public AjustesPersonalizacion ajustes() {
		return repository.findFirstByOrderByIdAsc().orElseGet(AjustesPersonalizacion::new);
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CATALOGO, allEntries = true)
	public AjustesPersonalizacion guardar(AjustesPersonalizacion datos) {
		AjustesPersonalizacion ajustes = repository.findFirstByOrderByIdAsc().orElseGet(AjustesPersonalizacion::new);
		ajustes.setNombre(datos.getNombre().trim());
		ajustes.setPrecio(datos.getPrecio());
		ajustes.setDetalle(StringUtils.hasText(datos.getDetalle()) ? datos.getDetalle().trim() : null);
		return repository.save(ajustes);
	}

	/**
	 * Nombre y precio vigentes para personalizar un producto. Recibe los ajustes ya leídos para que el checkout
	 * no los consulte una vez por línea.
	 */
	public Aplicada aplicar(Producto producto, AjustesPersonalizacion ajustes) {
		if (!propiedades.habilitada()) {
			throw new ReglaNegocioException("La tienda no ofrece personalizar los productos");
		}
		if (producto.isServicio()) {
			throw new ReglaNegocioException(producto.getNombre() + " no se puede personalizar");
		}
		return new Aplicada(ajustes.getNombre(), ajustes.getPrecio());
	}
}
