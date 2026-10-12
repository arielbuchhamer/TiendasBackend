package com.tiendas.emails;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tiendas.comun.ServicioExternoException;

/** Envío por la API HTTP de Resend (no SMTP: Railway bloquea los puertos SMTP salvo en el plan Pro). */
@Component
@EmailHabilitado
class ResendClient {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Respuesta(String id) {
	}

	private final RestClient restClient;
	private final String remitente;

	ResendClient(EmailProperties email) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(20));
		this.restClient = RestClient.builder()
				.requestFactory(fabrica)
				.bufferContent((uri, metodo) -> true) // envía Content-Length en lugar de chunked
				.baseUrl(email.resend().url())
				.defaultHeaders(headers -> headers.setBearerAuth(email.resend().apiKey()))
				.build();
		this.remitente = email.remitente();
	}

	/** Envía el email y devuelve su id en Resend. */
	String enviar(EmailPendiente email) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("from", remitente);
		cuerpo.put("to", Arrays.stream(email.getDestinatarios().split(",")).map(String::trim).toList());
		cuerpo.put("subject", email.getAsunto());
		cuerpo.put("html", email.getHtml());
		cuerpo.put("text", email.getTexto());
		if (StringUtils.hasText(email.getResponderA())) {
			cuerpo.put("reply_to", email.getResponderA());
		}
		try {
			Respuesta respuesta = restClient.post().uri("/emails")
					// Si se cortó la respuesta y se reintenta, Resend no lo envía dos veces (ventana de 24 h)
					.header("Idempotency-Key", "email-" + email.getId())
					.body(cuerpo)
					.retrieve()
					.body(Respuesta.class);
			return respuesta == null ? null : respuesta.id();
		} catch (RestClientResponseException e) {
			throw new ServicioExternoException("Resend respondió " + e.getStatusCode() + ": "
					+ e.getResponseBodyAsString(), e);
		} catch (RestClientException e) {
			throw new ServicioExternoException("Error comunicando con Resend: " + e.getMostSpecificCause(), e);
		}
	}
}
