package com.tiendas.clientes;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.tiendas.comun.Consultas;

import jakarta.persistence.criteria.Predicate;

/** Filtros del listado de clientes del panel ({@code ?estado=ACTIVO&texto=perez}); solo se agregan los presentes. */
record FiltroClientes(EstadoCliente estado, String texto) {

	Specification<Cliente> comoSpecification() {
		return (cliente, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (estado != null) {
				condiciones.add(cb.equal(cliente.get("estado"), estado));
			}
			if (StringUtils.hasText(texto)) {
				// busqueda (email, nombre, documento, razón social) ya está en minúsculas: usa ix_cliente_busqueda_trgm
				condiciones.add(cb.like(cliente.get("busqueda"), Consultas.patronContiene(texto), Consultas.ESCAPE_LIKE));
			}
			return cb.and(condiciones.toArray(Predicate[]::new));
		};
	}
}
