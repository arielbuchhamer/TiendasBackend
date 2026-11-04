package com.tiendas.clientes;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra los links de clientes vencidos hace más de un mes (usados o no): ya no sirven y solo agrandan la tabla.
 * Se conservan un tiempo para poder revisar envíos recientes si un cliente dice que no le llegó el link.
 */
@Component
@ClientesHabilitado
class LimpiezaTokens {

	private static final Logger log = LoggerFactory.getLogger(LimpiezaTokens.class);
	private static final Duration RETENCION = Duration.ofDays(30);

	private final TokensCliente tokens;

	LimpiezaTokens(TokensCliente tokens) {
		this.tokens = tokens;
	}

	@Scheduled(cron = "0 45 4 * * *")
	@Transactional
	public void borrarVencidos() {
		int borrados = tokens.borrarVencidosAntesDe(Instant.now().minus(RETENCION));
		if (borrados > 0) {
			log.info("{} links de clientes vencidos hace más de {} días borrados", borrados, RETENCION.toDays());
		}
	}
}
