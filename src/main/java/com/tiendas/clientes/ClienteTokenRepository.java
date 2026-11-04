package com.tiendas.clientes;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ClienteTokenRepository extends JpaRepository<ClienteToken, Long> {

	Optional<ClienteToken> findByHash(String hash);

	/** Solo vale el último link: al generar uno nuevo, los anteriores del mismo tipo quedan usados. */
	@Modifying
	@Query("UPDATE ClienteToken t SET t.usadoEn = :ahora"
			+ " WHERE t.clienteId = :clienteId AND t.tipo = :tipo AND t.usadoEn IS NULL")
	void invalidarAnteriores(Long clienteId, TipoTokenCliente tipo, Instant ahora);

	@Modifying
	@Query("DELETE FROM ClienteToken t WHERE t.venceEn < :limite")
	int borrarVencidosAntesDe(Instant limite);
}
