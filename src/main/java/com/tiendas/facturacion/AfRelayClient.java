package com.tiendas.facturacion;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP de AFRelay, el servicio que firma (WSAA) y envía los requests a los web services de ARCA. */
@Component
@FacturacionHabilitada
class AfRelayClient {

	private final RestClient restClient;
	private final JsonMapper jsonMapper;

	AfRelayClient(JsonMapper jsonMapper, FacturacionProperties facturacion) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(60)); // ARCA puede tardar en responder
		this.restClient = RestClient.builder()
				.requestFactory(fabrica)
				.baseUrl(facturacion.afrelay().url())
				.defaultHeaders(headers -> headers.setBearerAuth(facturacion.afrelay().token()))
				.build();
		this.jsonMapper = jsonMapper;
	}

	JsonNode renovarTicketAcceso() {
		return post("/wsaa/loginCms", null);
	}

	JsonNode obtenerPuntosVenta(long cuit) {
		return post("/wsfev1/FEParamGetPtosVenta", Map.of("Auth", Map.of("Cuit", cuit)));
	}

	JsonNode obtenerUltimoAutorizado(long cuit, int puntoVenta, TipoComprobante tipo) {
		return post("/wsfev1/FECompUltimoAutorizado", Map.of(
				"Auth", Map.of("Cuit", cuit),
				"PtoVta", puntoVenta,
				"CbteTipo", tipo.getCodigoArca()));
	}

	JsonNode solicitarCae(Map<String, Object> request) {
		return post("/wsfev1/FECAESolicitar", request);
	}

	JsonNode consultarComprobante(long cuit, int puntoVenta, TipoComprobante tipo, long numero) {
		return post("/wsfev1/FECompConsultar", Map.of(
				"Auth", Map.of("Cuit", cuit),
				"FeCompConsReq", Map.of("PtoVta", puntoVenta, "CbteTipo", tipo.getCodigoArca(), "CbteNro", numero)));
	}

	String estado() {
		try {
			String respuesta = restClient.get().uri("/wsfev1/health/readiness").retrieve().body(String.class);
			return respuesta == null ? "" : respuesta;
		} catch (RestClientException e) {
			throw new AfRelayException("AFRelay no responde", e);
		}
	}

	private JsonNode post(String endpoint, Object request) {
		try {
			RestClient.RequestBodySpec spec = restClient.post().uri(endpoint);
			if (request != null) {
				spec.body(request);
			}
			String respuesta = spec.retrieve().body(String.class);
			return respuesta == null || respuesta.isBlank() ? jsonMapper.createObjectNode() : jsonMapper.readTree(respuesta);
		} catch (RestClientResponseException e) {
			throw new AfRelayException("AFRelay respondió " + e.getStatusCode() + " en " + endpoint + ": "
					+ e.getResponseBodyAsString(), e);
		} catch (RestClientException e) {
			throw new AfRelayException("Error comunicando con AFRelay en " + endpoint, e);
		} catch (JacksonException e) {
			throw new AfRelayException("AFRelay respondió un JSON inválido en " + endpoint, e);
		}
	}
}
