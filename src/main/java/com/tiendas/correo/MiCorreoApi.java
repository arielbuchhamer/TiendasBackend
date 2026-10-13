package com.tiendas.correo;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tiendas.comun.ServicioExternoException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la API de MiCorreo (manual "API MiCorreo", versión 2026-05). Autenticación: las credenciales de la API
 * (HTTP Basic) se cambian por un JWT en POST /token, que se reutiliza hasta que vence o la API responde 401.
 * <p>
 * Las cotizaciones se guardan 30 minutos y las sucursales 12 horas: el calculador de envío consulta mientras el
 * cliente navega y no hace falta pegarle al Correo en cada página.
 */
class MiCorreoApi implements CorreoArgentino {

	private static final Logger log = LoggerFactory.getLogger(MiCorreoApi.class);
	private static final Duration DURACION_TOKEN = Duration.ofMinutes(50);

	private final CorreoArgentinoProperties config;
	private final JsonMapper json;
	private final RestClient rest;

	private final Cache<String, List<Tarifa>> cotizaciones = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(30)).maximumSize(5_000).build();
	private final Cache<String, List<Sucursal>> sucursalesPorProvincia = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofHours(12)).build();

	private String token;
	private Instant tokenVence = Instant.EPOCH;

	MiCorreoApi(CorreoArgentinoProperties config, JsonMapper json) {
		this.config = config;
		this.json = json;
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		fabrica.setReadTimeout(Duration.ofSeconds(20));
		this.rest = RestClient.builder().requestFactory(fabrica).baseUrl(config.url()).build();
	}

	@Override
	public List<Tarifa> cotizar(String codigoPostalDestino, List<Paquete> paquetes) {
		return cotizaciones.get(codigoPostalDestino + "|" + paquetes, clave -> pedirCotizacion(codigoPostalDestino, paquetes));
	}

	private List<Tarifa> pedirCotizacion(String codigoPostalDestino, List<Paquete> paquetes) {
		Map<String, Object> pedido = new LinkedHashMap<>();
		pedido.put("customerId", config.numeroCliente());
		pedido.put("postalCodeOrigin", config.codigoPostalOrigen());
		pedido.put("postalCodeDestination", codigoPostalDestino);
		pedido.put("deliveredType", null); // las dos: domicilio y sucursal
		pedido.put("dimensions", paquetes.stream().map(p -> Map.of(
				"quantity", p.cantidad(), "weight", p.pesoGramos(), "height", p.altoCm(),
				"width", p.anchoCm(), "length", p.largoCm())).toList());
		JsonNode respuesta = llamar(HttpMethod.POST, "/rates/packages", pedido, null);

		List<Tarifa> tarifas = new ArrayList<>();
		for (JsonNode tarifa : respuesta.path("rates")) {
			tarifas.add(new Tarifa(
					tarifa.path("deliveredType").asString(),
					tarifa.path("productType").asString(),
					tarifa.path("productName").asString(),
					tarifa.path("price").decimalValue(),
					entero(tarifa.path("deliveryTimeMin")),
					entero(tarifa.path("deliveryTimeMax"))));
		}
		return List.copyOf(tarifas);
	}

	@Override
	public List<Sucursal> sucursales(String codigoProvincia) {
		return sucursalesPorProvincia.get(codigoProvincia, clave -> pedirSucursales(codigoProvincia));
	}

	private List<Sucursal> pedirSucursales(String codigoProvincia) {
		JsonNode respuesta = llamar(HttpMethod.GET, "/agencies", null,
				Map.of("customerId", config.numeroCliente(), "provinceCode", codigoProvincia));
		List<Sucursal> sucursales = new ArrayList<>();
		for (JsonNode s : respuesta) {
			boolean activa = "ACTIVE".equalsIgnoreCase(s.path("status").asString());
			if (!activa || !s.path("services").path("pickupAvailability").asBoolean(false)) {
				continue;
			}
			JsonNode d = s.path("location").path("address");
			sucursales.add(new Sucursal(
					s.path("code").asString(),
					s.path("name").asString(),
					(d.path("streetName").asString("") + " " + d.path("streetNumber").asString("")).trim(),
					d.path("locality").asString(d.path("city").asString("")),
					d.path("province").asString(""),
					d.path("postalCode").asString(null)));
		}
		return List.copyOf(sucursales);
	}

	@Override
	public void darDeAlta(AltaEnvio envio) {
		Map<String, Object> direccion = new LinkedHashMap<>();
		direccion.put("streetName", envio.calle());
		direccion.put("streetNumber", envio.numero());
		direccion.put("floor", recortar(envio.piso(), 3));
		direccion.put("apartment", recortar(envio.departamento(), 3));
		direccion.put("city", envio.ciudad());
		direccion.put("provinceCode", envio.provincia());
		direccion.put("postalCode", envio.codigoPostal());

		Map<String, Object> envioCorreo = new LinkedHashMap<>();
		envioCorreo.put("deliveryType", envio.entrega());
		envioCorreo.put("productType", envio.servicio());
		envioCorreo.put("agency", envio.sucursal());
		envioCorreo.put("address", "S".equals(envio.entrega()) ? null : direccion);
		envioCorreo.put("weight", envio.paquete().pesoGramos());
		envioCorreo.put("declaredValue", envio.valorDeclarado());
		envioCorreo.put("height", envio.paquete().altoCm());
		envioCorreo.put("length", envio.paquete().largoCm());
		envioCorreo.put("width", envio.paquete().anchoCm());

		Map<String, Object> destinatario = new LinkedHashMap<>();
		destinatario.put("name", envio.destinatario());
		destinatario.put("email", envio.email());
		destinatario.put("cellPhone", envio.telefono());

		Map<String, Object> pedido = new LinkedHashMap<>();
		pedido.put("customerId", config.numeroCliente());
		pedido.put("extOrderId", envio.idExterno());
		pedido.put("orderNumber", envio.numeroPedido());
		pedido.put("recipient", destinatario);
		pedido.put("shipping", envioCorreo);
		// Sin "sender": el Correo usa el domicilio de remitente cargado en el perfil de MiCorreo
		llamar(HttpMethod.POST, "/shipping/import", pedido, null);
	}

	@Override
	public List<EventoSeguimiento> seguimiento(String codigoSeguimiento) {
		// El manual define este GET con el id en el cuerpo
		JsonNode respuesta = llamar(HttpMethod.GET, "/shipping/tracking", Map.of("shippingId", codigoSeguimiento), null);
		JsonNode envio = respuesta.isArray() ? respuesta.path(0) : respuesta;
		if (envio.hasNonNull("error")) {
			throw new ServicioExternoException("Correo Argentino: " + envio.path("error").asString());
		}
		List<EventoSeguimiento> eventos = new ArrayList<>();
		for (JsonNode e : envio.path("events")) {
			eventos.add(new EventoSeguimiento(e.path("date").asString(), e.path("event").asString(),
					e.path("branch").asString("")));
		}
		return eventos;
	}

	// ─── HTTP ───────────────────────────────────────────────────────────

	private JsonNode llamar(HttpMethod metodo, String ruta, Object cuerpo, Map<String, String> parametros) {
		try {
			return enviar(metodo, ruta, cuerpo, parametros, token());
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
				invalidarToken(); // el token venció antes de lo esperado: se pide uno nuevo y se reintenta una vez
				return reintentar(metodo, ruta, cuerpo, parametros);
			}
			throw error(ruta, e);
		}
	}

	private JsonNode reintentar(HttpMethod metodo, String ruta, Object cuerpo, Map<String, String> parametros) {
		try {
			return enviar(metodo, ruta, cuerpo, parametros, token());
		} catch (RestClientResponseException e) {
			throw error(ruta, e);
		}
	}

	private JsonNode enviar(HttpMethod metodo, String ruta, Object cuerpo, Map<String, String> parametros, String jwt) {
		try {
			RestClient.RequestBodySpec spec = rest.method(metodo)
					.uri(uri -> {
						uri.path(ruta);
						if (parametros != null) {
							parametros.forEach(uri::queryParam);
						}
						return uri.build();
					})
					.headers(h -> h.setBearerAuth(jwt))
					.accept(MediaType.APPLICATION_JSON);
			if (cuerpo != null) {
				spec.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
			}
			String respuesta = spec.retrieve().body(String.class);
			return respuesta == null || respuesta.isBlank() ? json.createObjectNode() : json.readTree(respuesta);
		} catch (RestClientResponseException e) {
			throw e;
		} catch (RestClientException e) {
			throw new ServicioExternoException("No se pudo conectar con Correo Argentino (" + ruta + ")", e);
		} catch (JacksonException e) {
			throw new ServicioExternoException("Correo Argentino respondió un JSON inválido en " + ruta, e);
		}
	}

	private synchronized String token() {
		if (token != null && Instant.now().isBefore(tokenVence)) {
			return token;
		}
		try {
			String respuesta = rest.post().uri("/token")
					.headers(h -> h.setBasicAuth(config.usuario(), config.clave()))
					.retrieve().body(String.class);
			token = json.readTree(respuesta).path("token").asString();
			tokenVence = Instant.now().plus(DURACION_TOKEN);
			log.debug("Correo Argentino: token renovado");
			return token;
		} catch (RestClientResponseException e) {
			throw new ServicioExternoException("Correo Argentino rechazó las credenciales de la API (HTTP "
					+ e.getStatusCode().value() + "). Revisá CORREO_ARGENTINO_USUARIO y CORREO_ARGENTINO_CLAVE.", e);
		} catch (RestClientException | JacksonException e) {
			throw new ServicioExternoException("No se pudo obtener el token de Correo Argentino", e);
		}
	}

	private synchronized void invalidarToken() {
		token = null;
		tokenVence = Instant.EPOCH;
	}

	// Los errores de negocio del Correo vienen como {"code":"402","message":"..."}: el mensaje es el que sirve
	private ServicioExternoException error(String ruta, RestClientResponseException e) {
		String mensaje = e.getResponseBodyAsString();
		try {
			JsonNode cuerpo = json.readTree(mensaje);
			if (cuerpo.hasNonNull("message")) {
				mensaje = cuerpo.path("message").asString();
			}
		} catch (JacksonException ignorada) {
			// se usa el cuerpo tal cual
		}
		return new ServicioExternoException("Correo Argentino respondió " + e.getStatusCode().value() + " en " + ruta
				+ ": " + mensaje, e);
	}

	private static Integer entero(JsonNode nodo) {
		try {
			return nodo.isMissingNode() || nodo.isNull() ? null : Integer.valueOf(nodo.asString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String recortar(String texto, int maximo) {
		return texto == null ? null : texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
