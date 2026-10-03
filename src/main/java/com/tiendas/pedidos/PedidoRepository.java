package com.tiendas.pedidos;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	Page<Pedido> findByEstado(EstadoPedido estado, Pageable pagina);

	long countByEstadoAndVistoFalse(EstadoPedido estado);

	/** SELECT ... FOR UPDATE: serializa las notificaciones de pago concurrentes de un mismo pedido. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Pedido p WHERE p.codigo = :codigo")
	Optional<Pedido> bloquearPorCodigo(UUID codigo);

	@Modifying
	@Query("UPDATE Pedido p SET p.mpPreferenceId = :referencia, p.checkoutUrl = :url, p.actualizadoEn = CURRENT_TIMESTAMP"
			+ " WHERE p.id = :id")
	void registrarCheckout(Long id, String referencia, String url);

	@Modifying
	@Query("UPDATE Pedido p SET p.estado = :estado, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.id = :id")
	void cambiarEstado(Long id, EstadoPedido estado);

	@Modifying
	@Query("UPDATE Pedido p SET p.estado = com.tiendas.pedidos.EstadoPedido.VENCIDO, p.actualizadoEn = CURRENT_TIMESTAMP"
			+ " WHERE p.estado = com.tiendas.pedidos.EstadoPedido.PENDIENTE AND p.creadoEn < :limite")
	int vencerPendientesAnterioresA(Instant limite);
}
