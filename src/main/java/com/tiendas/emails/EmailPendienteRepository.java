package com.tiendas.emails;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailPendienteRepository extends JpaRepository<EmailPendiente, Long> {

	@Query("SELECT e FROM EmailPendiente e WHERE e.estado = com.tiendas.emails.EstadoEmail.PENDIENTE"
			+ " AND e.proximoIntento <= :ahora ORDER BY e.proximoIntento, e.id")
	List<EmailPendiente> buscarParaEnviar(Instant ahora, Limit limite);

	/** Borra los ya procesados: la cola guarda datos personales del cliente y no hace falta conservarlos. */
	@Modifying
	@Query("DELETE FROM EmailPendiente e WHERE e.estado <> com.tiendas.emails.EstadoEmail.PENDIENTE"
			+ " AND e.creadoEn < :limite")
	int borrarProcesadosAnterioresA(Instant limite);
}
