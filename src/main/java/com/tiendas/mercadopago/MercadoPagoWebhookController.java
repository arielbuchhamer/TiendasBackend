package com.tiendas.mercadopago;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.pedidos.PasarelaPago.PagoInformado;
import com.tiendas.pedidos.PedidoService;

/**
 * Notificaciones de Mercado Pago. Doble verificación:
 * <ol>
 * <li>la firma HMAC del header x-signature (descarta notificaciones falsificadas sin costo), y</li>
 * <li>el estado real del pago se consulta siempre a la API de MP; nunca se confía en el contenido
 * de la notificación.</li>
 * </ol>
 * Responde 2xx solo si el pago quedó registrado; ante un error responde 5xx y MP reintenta.
 */
@RestController
class MercadoPagoWebhookController {

	private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

	private final FirmaWebhook firmaWebhook;
	private final MercadoPagoPasarela mercadoPago;
	private final PedidoService pedidoService;

	MercadoPagoWebhookController(FirmaWebhook firmaWebhook, MercadoPagoPasarela mercadoPago, PedidoService pedidoService) {
		this.firmaWebhook = firmaWebhook;
		this.mercadoPago = mercadoPago;
		this.pedidoService = pedidoService;
	}

	@PostMapping("/api/v1/webhooks/mercadopago")
	ResponseEntity<Void> recibir(@RequestParam Map<String, String> parametros,
			@RequestHeader(name = "x-signature", required = false) String firma,
			@RequestHeader(name = "x-request-id", required = false) String requestId) {
		String tipo = Optional.ofNullable(parametros.get("type")).orElse(parametros.get("topic"));
		String dataId = Optional.ofNullable(parametros.get("data.id")).orElse(parametros.get("id"));

		if (!"payment".equals(tipo)) {
			return ResponseEntity.ok().build(); // otros tópicos (merchant_order, etc.) no se usan
		}
		if (!firmaWebhook.esValida(firma, requestId, dataId)) {
			log.warn("Notificación de Mercado Pago con firma inválida (data.id={}, request-id={})", dataId, requestId);
			return ResponseEntity.status(401).build();
		}

		Optional<PagoInformado> pago = mercadoPago.obtenerPago(dataId);
		if (pago.isEmpty()) {
			log.warn("Mercado Pago notificó el pago {} pero no existe en la cuenta: se ignora", dataId);
			return ResponseEntity.ok().build();
		}
		pedidoService.registrarPago(pago.get());
		log.info("Pago {} registrado: {} ({})", dataId, pago.get().estado(), pago.get().referenciaPedido());
		return ResponseEntity.ok().build();
	}
}
