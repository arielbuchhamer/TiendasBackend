package com.tiendas.zipnova;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Avisos de Zipnova (webhooks de los tópicos {@code status} y {@code shipment}): actualizan el estado del envío de la
 * venta sin que el vendedor tenga que consultar. Zipnova no firma los avisos, así que la URL lleva un secreto y el
 * cuerpo solo se usa para saber qué envío consultar a la API. Siempre responde 200 a un aviso válido (si no, Zipnova
 * reintenta cada hora durante 12 horas).
 */
@RestController
@ConditionalOnBooleanProperty("zipnova.habilitada")
class ZipnovaWebhookController {

	private static final Logger log = LoggerFactory.getLogger(ZipnovaWebhookController.class);

	private final ZipnovaEnvios zipnovaEnvios;
	private final byte[] secreto;

	ZipnovaWebhookController(ZipnovaEnvios zipnovaEnvios, ZipnovaProperties config) {
		this.zipnovaEnvios = zipnovaEnvios;
		String valor = config.webhookSecreto();
		this.secreto = valor == null || valor.length() < 32 ? null : valor.getBytes(StandardCharsets.UTF_8);
	}

	@PostMapping("/api/v1/webhooks/zipnova/{secreto}")
	ResponseEntity<Void> aviso(@PathVariable String secreto, @RequestBody(required = false) JsonNode cuerpo) {
		if (this.secreto == null || !MessageDigest.isEqual(this.secreto, secreto.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		String topico = cuerpo == null ? "" : cuerpo.path("topic").asString("");
		JsonNode id = cuerpo == null ? null : cuerpo.path("data").path("shipment_id");
		if (("status".equals(topico) || "shipment".equals(topico)) && id != null && id.isIntegralNumber()) {
			try {
				zipnovaEnvios.avisoDeEnvio(id.asString());
			} catch (RuntimeException e) {
				// Si Zipnova no responde, el aviso igual se da por recibido: el estado se actualiza al consultar
				log.warn("No se pudo actualizar el envío {} de Zipnova: {}", id.asString(), e.getMessage());
			}
		}
		return ResponseEntity.ok().build();
	}
}
