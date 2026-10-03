package com.tiendas.catalogo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

	@Query("SELECT v FROM Variante v JOIN FETCH v.producto WHERE v.id IN :ids")
	List<Variante> buscarConProducto(Collection<Long> ids);

	/**
	 * SELECT ... FOR UPDATE: bloquea las filas hasta el fin de la transacción para descontar stock sin
	 * condiciones de carrera. Se ordena por id para que dos transacciones siempre bloqueen en el mismo
	 * orden y no se produzcan deadlocks.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT v FROM Variante v WHERE v.id IN :ids ORDER BY v.id")
	List<Variante> bloquearParaActualizar(Collection<Long> ids);
}
