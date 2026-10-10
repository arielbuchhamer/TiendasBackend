package com.tiendas.descuentos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Los usos se cuentan sobre los pedidos pagados (aprobados o pagados pero en revisión): un pedido que
 * nunca se pagó no consume el cupón.
 */
public interface CuponRepository extends JpaRepository<Cupon, Long> {

	String PAGADOS = "(com.tiendas.pedidos.EstadoPedido.APROBADO, com.tiendas.pedidos.EstadoPedido.REQUIERE_REVISION)";

	/** Métricas de un cupón: cantidad de pedidos pagados con él, total cobrado y total descontado. */
	interface Metricas {
		Long getCuponId();

		long getUsos();

		BigDecimal getTotalVendido();

		BigDecimal getTotalDescontado();
	}

	List<Cupon> findAllByOrderByCreadoEnDesc();

	Optional<Cupon> findByCodigo(String codigo);

	boolean existsByCodigoAndIdNot(String codigo, Long id);

	boolean existsByCodigo(String codigo);

	@Query("SELECT p.cuponId AS cuponId, COUNT(p) AS usos, COALESCE(SUM(p.total), 0) AS totalVendido,"
			+ " COALESCE(SUM(p.descuento), 0) AS totalDescontado"
			+ " FROM Pedido p WHERE p.cuponId IS NOT NULL AND p.estado IN " + PAGADOS + " GROUP BY p.cuponId")
	List<Metricas> metricas();

	@Query("SELECT COUNT(p) FROM Pedido p WHERE p.cuponId = :cuponId AND p.estado IN " + PAGADOS)
	long contarUsos(Long cuponId);

	@Query("SELECT COUNT(p) FROM Pedido p WHERE p.cuponId = :cuponId AND p.estado IN " + PAGADOS
			+ " AND lower(p.cliente.email) = lower(:email)")
	long contarUsosDeCliente(Long cuponId, String email);

	@Query("SELECT COUNT(p) > 0 FROM Pedido p WHERE p.estado IN " + PAGADOS + " AND lower(p.cliente.email) = lower(:email)")
	boolean clienteYaCompro(String email);

	@Query("SELECT COUNT(p) > 0 FROM Pedido p WHERE p.cuponId = :cuponId")
	boolean tienePedidos(Long cuponId);
}
