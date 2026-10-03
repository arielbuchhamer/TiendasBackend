package com.tiendas.pedidos;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.config.TiendaProperties;

/** Pasa a VENCIDO los pedidos que quedaron pendientes de pago más tiempo del configurado. */
@Component
class VencimientoPedidos {

	private static final Logger log = LoggerFactory.getLogger(VencimientoPedidos.class);

	private final PedidoRepository pedidoRepository;
	private final TiendaProperties tienda;

	VencimientoPedidos(PedidoRepository pedidoRepository, TiendaProperties tienda) {
		this.pedidoRepository = pedidoRepository;
		this.tienda = tienda;
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
	@Transactional
	public void vencerPendientes() {
		Instant limite = Instant.now().minus(Duration.ofHours(tienda.pedidos().horasVencimiento()));
		int vencidos = pedidoRepository.vencerPendientesAnterioresA(limite);
		if (vencidos > 0) {
			log.info("{} pedidos pendientes pasaron a VENCIDO", vencidos);
		}
	}
}
