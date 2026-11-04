package com.tiendas.comun;

import java.util.Set;

import org.springframework.data.domain.Sort;

/** Piezas comunes de los listados con filtros: búsqueda por texto y orden elegido por el cliente. */
public final class Consultas {

	/** Carácter de escape de {@link #patronContiene}; se pasa a {@code CriteriaBuilder#like}. */
	public static final char ESCAPE_LIKE = '\\';

	private Consultas() {
	}

	/** {@code %texto%} en minúsculas, con los comodines de LIKE escapados (un "%" buscado no matchea todo). */
	public static String patronContiene(String texto) {
		String escapado = Textos.minusculas(texto)
				.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escapado + "%";
	}

	/**
	 * Valida el orden pedido contra una lista blanca (ordenar por cualquier campo permitiría inferir datos internos
	 * o forzar consultas sin índice) y le agrega el desempate, para que la paginación no repita ni saltee filas.
	 */
	public static Sort ordenPermitido(Sort pedido, Set<String> permitidos, Sort desempate) {
		if (pedido.isUnsorted()) {
			return desempate;
		}
		for (Sort.Order orden : pedido) {
			if (!permitidos.contains(orden.getProperty())) {
				throw new ReglaNegocioException("No se puede ordenar por " + orden.getProperty()
						+ ". Opciones: " + permitidos);
			}
		}
		return pedido.and(desempate);
	}
}
