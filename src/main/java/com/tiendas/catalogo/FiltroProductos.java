package com.tiendas.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtros del listado de productos; se arman desde los query params ({@code ?rubroId=1&texto=mesa}).
 * Solo se agregan al SQL los filtros presentes, así cada consulta usa el índice que le corresponde.
 */
public record FiltroProductos(Long rubroId, Long categoriaId, String texto, Boolean destacados) {

	Specification<Producto> comoSpecification(boolean incluirInactivos) {
		return (producto, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (!incluirInactivos) {
				condiciones.add(cb.isTrue(producto.get("activo")));
			}
			if (categoriaId != null) {
				condiciones.add(cb.equal(producto.get("categoriaId"), categoriaId));
			}
			if (rubroId != null) {
				Subquery<Long> categoriasDelRubro = consulta.subquery(Long.class);
				Root<Categoria> categoria = categoriasDelRubro.from(Categoria.class);
				categoriasDelRubro.select(categoria.get("id")).where(cb.equal(categoria.get("rubroId"), rubroId));
				condiciones.add(producto.get("categoriaId").in(categoriasDelRubro));
			}
			if (StringUtils.hasText(texto)) {
				// lower(nombre) LIKE '%texto%' usa el índice trigram ix_producto_nombre_trgm
				String patron = "%" + escaparLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
				condiciones.add(cb.like(cb.lower(producto.get("nombre")), patron, '\\'));
			}
			if (Boolean.TRUE.equals(destacados)) {
				condiciones.add(cb.isTrue(producto.get("destacado")));
			}
			return cb.and(condiciones.toArray(Predicate[]::new));
		};
	}

	private static String escaparLike(String texto) {
		return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
