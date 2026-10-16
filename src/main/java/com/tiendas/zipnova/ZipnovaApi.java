package com.tiendas.zipnova;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tiendas.comun.ReglaNegocioException;
import com.tiendas.comun.ServicioExternoException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la API de envíos v2 de Zipnova (https://docs.zipnova.com/envios). Autenticación HTTP Basic con el token
 * y el secret de la cuenta.
 * <p>
 * Las cotizaciones se guardan 30 minutos: el calculador de envío consulta mientras el cliente navega y Zipnova limita
 * la cantidad de cotizaciones por minuto.
 */
class ZipnovaApi implements Zipnova {

	private static final Logger log = LoggerFactory.getLogger(ZipnovaApi.class);

	/** Identifica a esta integración en el panel de Zipnova (sirve para filtrar en su motor de reglas). */
	private static final String FUENTE = "tiendas-backend";
	/** Zipnova rechaza ítems de menos de 10 gramos. */
	private static final int PESO_MINIMO = 10;
	/** Zipnova acepta hasta 1000 ítems por cotización. */
	private static final int MAXIMO_ITEMS = 1000;

	private final ZipnovaProperties config;
	private final JsonMapper json;
	private final RestClient rest;

	private final Cache<String, Cotizacion> cotizaciones = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(30)).maximumSize(5_000).build();

	private volatile Long cuentaId;
	private volatile Long origenId;
	private volatile String codigoPostalOrigen;

	ZipnovaApi(ZipnovaProperties config, JsonMapper json) {
		this.config = config;
		this.json = json;
		this.cuentaId = config.cuentaId();
		this.origenId = config.origenId();
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		// El alta puede tardar: Zipnova vuelve a cotizar y le pide el número de seguimiento al transporte
		fabrica.setReadTimeout(Duration.ofSeconds(25));
		this.rest = RestClient.builder()
				.requestFactory(fabrica)
				.baseUrl(config.url())
				.defaultHeaders(h -> h.setBasicAuth(config.apiToken(), config.apiSecret(), StandardCharsets.UTF_8))
				.build();
	}

	// ─── Cotización ─────────────────────────────────────────────────────

	@Override
	public Cotizacion cotizar(Destino destino, List<Item> items, BigDecimal valorDeclarado) {
		String clave = destino + "|" + items + "|" + valorDeclarado;
		return cotizaciones.get(clave, c -> pedirCotizacion(destino, items, valorDeclarado));
	}

	private Cotizacion pedirCotizacion(Destino destino, List<Item> items, BigDecimal valorDeclarado) {
		Map<String, Object> pedido = new LinkedHashMap<>();
		pedido.put("account_id", cuenta());
		if (config.origenId() != null) {
			pedido.put("origin_id", config.origenId()); // sin origen, Zipnova usa el de la cuenta por defecto
		}
		pedido.put("source", FUENTE);
		pedido.put("declared_value", valorDeclarado.setScale(2, RoundingMode.HALF_UP));
		pedido.put("destination", destino(destino.codigoPostal(), destino.ciudad(), destino.provincia()));
		pedido.put("items", items(items));
		pedido.put("type_packaging", "dynamic");
		JsonNode respuesta = llamar(HttpMethod.POST, "/shipments/quote", pedido);

		// "all_results" trae cada forma de entrega con cada transporte habilitado en la cuenta ("results" solo la
		// mejor de cada forma). Se ofrecen todas las seleccionables: el cliente elige transporte, precio y plazo.
		Map<String, Opcion> opciones = new LinkedHashMap<>();
		for (JsonNode r : respuesta.path("all_results")) {
			if (!r.path("selectable").asBoolean(false)) {
				continue;
			}
			String servicio = r.path("service_type").path("code").asString("");
			List<Punto> puntos = new ArrayList<>();
			for (JsonNode p : r.path("pickup_points")) {
				JsonNode l = p.path("location");
				puntos.add(new Punto(
						p.path("point_id").asString(),
						p.path("description").asString(""),
						(l.path("street").asString("") + " " + l.path("street_number").asString("")).trim(),
						l.path("city").asString(""),
						l.path("state").asString(""),
						l.path("zipcode").asString(null),
						p.path("open_hours").asString(null)));
			}
			// Un retiro en sucursal sin sucursales no se puede dar de alta
			if (RETIRO_EN_PUNTO.equals(servicio) && puntos.isEmpty()) {
				continue;
			}
			JsonNode tiempos = r.path("delivery_time");
			String entrega = tiempos.path("estimated_delivery").asString("");
			Opcion opcion = new Opcion(
					servicio,
					r.path("service_type").path("name").asString(servicio),
					r.path("service_type").path("is_urgent").asBoolean(false),
					r.path("carrier").path("id").asLong(),
					r.path("carrier").path("name").asString("Zipnova"),
					r.path("carrier").path("logo").asString(null),
					r.path("logistic_type").asString(null),
					r.path("amounts").path("price_incl_tax").decimalValue().setScale(2, RoundingMode.HALF_UP),
					dias(tiempos.path("times").path("total").path("min"), tiempos.path("min")),
					dias(tiempos.path("times").path("total").path("max"), tiempos.path("max")),
					entrega.length() >= 10 ? entrega.substring(0, 10) : null,
					List.copyOf(puntos));
			// Un mismo transporte y servicio con varias logísticas: queda la más barata
			opciones.merge(opcion.transportistaId() + "|" + servicio, opcion,
					(una, otra) -> una.precio().compareTo(otra.precio()) <= 0 ? una : otra);
		}
		JsonNode resuelto = respuesta.path("destination");
		return new Cotizacion(resuelto.path("city").asString(null), resuelto.path("state").asString(null),
				List.copyOf(opciones.values()));
	}

	// ─── Envíos ─────────────────────────────────────────────────────────

	@Override
	public Envio darDeAlta(AltaEnvio envio) {
		boolean enPunto = RETIRO_EN_PUNTO.equals(envio.servicio());
		Map<String, Object> destino = new LinkedHashMap<>();
		destino.put("name", envio.destinatario());
		destino.put("document", envio.documento());
		destino.put("email", envio.email());
		destino.put("phone", envio.telefono());
		if (enPunto) {
			destino.put("point_id", Long.valueOf(envio.puntoId()));
		} else {
			destino.put("street", envio.calle());
			destino.put("street_number", envio.numero());
			destino.put("street_extras", envio.extras());
			destino.putAll(destino(envio.codigoPostal(), envio.ciudad(), envio.provincia()));
		}

		Map<String, Object> pedido = new LinkedHashMap<>();
		pedido.put("account_id", cuenta());
		pedido.put("origin_id", origen());
		pedido.put("external_id", envio.idExterno());
		pedido.put("source", FUENTE);
		pedido.put("service_type", envio.servicio());
		pedido.put("carrier_id", envio.transportistaId());
		if (StringUtils.hasText(envio.logistica())) {
			pedido.put("logistic_type", envio.logistica());
		}
		pedido.put("declared_value", envio.valorDeclarado().setScale(2, RoundingMode.HALF_UP));
		pedido.put("type_packaging", "dynamic");
		pedido.put("items", items(envio.items()));
		pedido.put("destination", destino);
		return envioDe(llamar(HttpMethod.POST, "/shipments", pedido));
	}

	@Override
	public Envio envio(String id) {
		return envioDe(llamar(HttpMethod.GET, "/shipments/" + numero(id), null));
	}

	@Override
	public byte[] etiqueta(String id) {
		ResponseEntity<byte[]> respuesta;
		try {
			respuesta = rest.get().uri("/shipments/{id}/label.pdf", numero(id)).retrieve().toEntity(byte[].class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new ReglaNegocioException("Zipnova todavía está generando la etiqueta: probá de nuevo en unos minutos");
			}
			throw error("/shipments/{id}/label.pdf", e);
		} catch (RestClientException e) {
			throw new ServicioExternoException("No se pudo conectar con Zipnova para bajar la etiqueta", e);
		}
		bloqueoDelFirewall(respuesta, "/shipments/{id}/label.pdf");
		return pdfDe(respuesta, "la etiqueta");
	}

	@Override
	public List<EventoSeguimiento> seguimiento(String id) {
		JsonNode respuesta = llamar(HttpMethod.GET, "/shipments/" + numero(id) + "/tracking?sort=newest", null);
		List<EventoSeguimiento> eventos = new ArrayList<>();
		for (JsonNode e : respuesta.isArray() ? respuesta : respuesta.path("data")) {
			JsonNode estado = e.path("status");
			String nombre = estado.path("visible_name").asString(estado.path("name").asString(""));
			String subestado = estado.path("substatus").asString("");
			eventos.add(new EventoSeguimiento(e.path("occurred_at").asString(""), nombre, subestado));
		}
		return eventos;
	}

	private static Envio envioDe(JsonNode e) {
		return new Envio(
				e.path("id").asString(),
				e.path("status_name").asString(e.path("status").asString("")),
				e.path("carrier").path("name").asString(null),
				e.path("carrier_tracking_id").asString(null),
				e.path("tracking").asString(e.path("tracking_external").asString(null)));
	}

	@Override
	public java.util.Optional<byte[]> guia(String id) {
		ResponseEntity<byte[]> respuesta;
		try {
			respuesta = rest.get().uri("/shipments/{id}/document.pdf", numero(id)).retrieve().toEntity(byte[].class);
		} catch (RestClientResponseException e) {
			// Zipnova responde 400 ("does not use shipping guide") cuando el transporte trabaja sin guía
			if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().toLowerCase().contains("guide")) {
				return java.util.Optional.empty();
			}
			if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new ReglaNegocioException("Zipnova todavía está generando la guía: probá de nuevo en unos minutos");
			}
			throw error("/shipments/{id}/document.pdf", e);
		} catch (RestClientException e) {
			throw new ServicioExternoException("No se pudo conectar con Zipnova para bajar la guía", e);
		}
		bloqueoDelFirewall(respuesta, "/shipments/{id}/document.pdf");
		return java.util.Optional.of(pdfDe(respuesta, "la guía"));
	}

	@Override
	public Cancelacion cancelar(String id) {
		JsonNode respuesta;
		try {
			respuesta = llamar(HttpMethod.POST, "/shipments/" + numero(id) + "/cancel", null);
		} catch (ServicioExternoException e) {
			// En este endpoint, 401 significa "no se puede cancelar en su estado actual", no credenciales malas
			if (e.getCause() instanceof RestClientResponseException r && r.getStatusCode().value() == 401) {
				throw new ReglaNegocioException("Zipnova no permite cancelar el envío en su estado actual");
			}
			throw e;
		}
		if (!respuesta.path("success").asBoolean(true)) {
			throw new ReglaNegocioException("Zipnova no pudo cancelar el envío");
		}
		return new Cancelacion("rescue_requested".equals(respuesta.path("result").asString("")));
	}

	@Override
	public List<Punto> puntosDeDespacho(String id) {
		String cp = codigoPostalOrigen();
		JsonNode respuesta = llamar(HttpMethod.GET, "/shipments/" + numero(id) + "/dropoff_locations"
				+ (cp == null ? "" : "?zipcode=" + cp), null);
		List<Punto> puntos = new ArrayList<>();
		for (JsonNode p : respuesta.path("data")) {
			puntos.add(new Punto(p.path("id").asString(), p.path("name").asString(""), p.path("address").asString(""),
					p.path("city").asString(""), p.path("state").asString(""), p.path("zipcode").asString(null), null));
		}
		return puntos;
	}

	@Override
	public Saldo saldo() {
		JsonNode r = llamar(HttpMethod.GET, "/accounts/" + cuenta() + "/billing", null);
		return new Saldo(r.path("balance").decimalValue(), r.path("credit_limit").decimalValue(),
				r.path("available").decimalValue());
	}

	/** Código postal del domicilio de la cuenta (el origen de los envíos), para buscar dónde despachar. */
	private String codigoPostalOrigen() {
		String cp = codigoPostalOrigen;
		if (cp == null) {
			cp = llamar(HttpMethod.GET, "/accounts/" + cuenta(), null).path("address").path("zipcode").asString("");
			codigoPostalOrigen = cp;
		}
		return cp.isBlank() ? null : cp;
	}

	/** Un PDF que Zipnova manda tal cual o como {"format": "pdf", "body": "<base64>"}. */
	private byte[] pdfDe(ResponseEntity<byte[]> respuesta, String que) {
		byte[] cuerpo = respuesta.getBody() == null ? new byte[0] : respuesta.getBody();
		MediaType tipo = respuesta.getHeaders().getContentType();
		if (tipo == null || !tipo.includes(MediaType.APPLICATION_JSON) && !tipo.getSubtype().endsWith("json")) {
			return cuerpo;
		}
		try {
			String base64 = json.readTree(cuerpo).path("body").asString("");
			byte[] pdf = base64.startsWith("%PDF") ? base64.getBytes(StandardCharsets.ISO_8859_1)
					: Base64.getMimeDecoder().decode(base64.replaceFirst("^data:[^,]*,", ""));
			if (pdf.length == 0) {
				throw new ReglaNegocioException("Zipnova todavía está generando " + que + ": probá de nuevo en unos minutos");
			}
			return pdf;
		} catch (JacksonException | IllegalArgumentException e) {
			throw new ServicioExternoException("Zipnova devolvió " + que + " en un formato que no se pudo leer", e);
		}
	}

	// ─── Cuenta y origen ────────────────────────────────────────────────

	/** La cuenta configurada o, si no hay, la única a la que dan acceso las credenciales. */
	private long cuenta() {
		Long id = cuentaId;
		if (id != null) {
			return id;
		}
		JsonNode cuentas = llamarSinCuenta("/accounts").path("data");
		if (cuentas.size() != 1) {
			throw new ServicioExternoException("Las credenciales de Zipnova dan acceso a " + cuentas.size()
					+ " cuentas: configurá ZIPNOVA_CUENTA_ID con la que corresponde");
		}
		cuentaId = cuentas.path(0).path("id").asLong();
		return cuentaId;
	}

	/** El origen configurado o la dirección de despacho por defecto de la cuenta. */
	private long origen() {
		Long id = origenId;
		if (id != null) {
			return id;
		}
		JsonNode ajustes = llamarSinCuenta("/accounts/" + cuenta() + "/settings");
		if (!ajustes.hasNonNull("default_forward_address_id")) {
			throw new ReglaNegocioException("La cuenta de Zipnova no tiene un origen de envíos por defecto: elegilo en "
					+ "Zipnova (Configuración > Orígenes por defecto)");
		}
		origenId = ajustes.path("default_forward_address_id").asLong();
		return origenId;
	}

	// ─── HTTP ───────────────────────────────────────────────────────────

	private JsonNode llamarSinCuenta(String ruta) {
		return llamar(HttpMethod.GET, ruta, null);
	}

	private JsonNode llamar(HttpMethod metodo, String ruta, Object cuerpo) {
		try {
			RestClient.RequestBodySpec spec = rest.method(metodo).uri(ruta).accept(MediaType.APPLICATION_JSON);
			if (cuerpo != null) {
				spec.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
			}
			ResponseEntity<String> entidad = spec.retrieve().toEntity(String.class);
			bloqueoDelFirewall(entidad, ruta);
			String respuesta = entidad.getBody();
			return respuesta == null || respuesta.isBlank() ? json.createObjectNode() : json.readTree(respuesta);
		} catch (RestClientResponseException e) {
			throw error(ruta, e);
		} catch (RestClientException e) {
			throw new ServicioExternoException("No se pudo conectar con Zipnova (" + sinIds(ruta) + "): "
					+ e.getMostSpecificCause().getMessage(), e);
		} catch (JacksonException e) {
			throw new ServicioExternoException("Zipnova respondió un JSON inválido en " + sinIds(ruta), e);
		}
	}

	/**
	 * El firewall de Zipnova (AWS WAF) responde 202 sin cuerpo y con {@code x-amzn-waf-action} a las conexiones que
	 * considera sospechosas, por ejemplo desde una VPN o un servidor fuera de Argentina. Sin este control se leería
	 * como una respuesta vacía (sin opciones de envío) y el motivo quedaría oculto.
	 */
	private static void bloqueoDelFirewall(ResponseEntity<?> respuesta, String ruta) {
		if (respuesta.getHeaders().containsHeader("x-amzn-waf-action")) {
			log.error("El firewall de Zipnova bloqueó la conexión en {} ({}): ¿VPN o servidor fuera de Argentina?",
					sinIds(ruta), respuesta.getHeaders().getFirst("x-amzn-waf-action"));
			throw new ServicioExternoException("Zipnova bloqueó la conexión desde este servidor (firewall). Pasa con "
					+ "VPN o con servidores fuera de Argentina");
		}
	}

	/**
	 * Solo el estado y el mensaje de Zipnova: los cuerpos llevan datos personales del comprador. Los rechazos de
	 * datos (400 y 422) se muestran al vendedor tal como los explica Zipnova.
	 */
	private RuntimeException error(String ruta, RestClientResponseException e) {
		String mensaje = mensajeDe(e.getResponseBodyAsString());
		int estado = e.getStatusCode().value();
		log.warn("Zipnova respondió {} en {}{}", estado, sinIds(ruta), mensaje == null ? "" : ": " + mensaje);
		if (estado == 401) {
			return new ServicioExternoException("Zipnova rechazó las credenciales de la API. Revisá ZIPNOVA_API_TOKEN y "
					+ "ZIPNOVA_API_SECRET.", e);
		}
		if (estado == 400 || estado == 406 || estado == 422) {
			return new ReglaNegocioException("Zipnova rechazó la operación" + (mensaje == null ? "" : ": " + mensaje));
		}
		return new ServicioExternoException("Zipnova respondió " + estado + " en " + sinIds(ruta), e);
	}

	private String mensajeDe(String cuerpo) {
		try {
			JsonNode nodo = json.readTree(cuerpo);
			String mensaje = nodo.path("message").asString(null);
			JsonNode errores = nodo.path("errors");
			if (errores.isObject() && !errores.isEmpty()) {
				List<String> detalles = new ArrayList<>();
				errores.properties().forEach(campo -> campo.getValue().forEach(d -> detalles.add(d.asString())));
				mensaje = String.join(" ", detalles);
			}
			return StringUtils.hasText(mensaje) ? recortar(mensaje, 300) : null;
		} catch (JacksonException e) {
			return null;
		}
	}

	// ─── Datos ──────────────────────────────────────────────────────────

	private static Map<String, Object> destino(String codigoPostal, String ciudad, String provincia) {
		Map<String, Object> destino = new LinkedHashMap<>();
		destino.put("zipcode", codigoPostal);
		if (StringUtils.hasText(ciudad)) {
			destino.put("city", ciudad.trim());
		}
		destino.put("state", provincia);
		return destino;
	}

	private static List<Map<String, Object>> items(List<Item> items) {
		if (items.size() > MAXIMO_ITEMS) {
			throw new ReglaNegocioException("Zipnova no cotiza más de " + MAXIMO_ITEMS + " unidades por envío");
		}
		return items.stream().map(i -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("weight", Math.max(PESO_MINIMO, i.pesoGramos()));
			item.put("height", Math.max(1, i.altoCm()));
			item.put("width", Math.max(1, i.anchoCm()));
			item.put("length", Math.max(1, i.largoCm()));
			if (StringUtils.hasText(i.descripcion())) {
				item.put("description", recortar(i.descripcion(), 60));
			}
			return item;
		}).toList();
	}

	/**
	 * Días hábiles de una duración ISO 8601. Zipnova suma al viaje las horas de preparación ("P4DT3H14M"): un día
	 * empezado cuenta entero, igual que en los campos viejos {@code min}/{@code max} (que van a dejar de venir).
	 */
	static Integer dias(JsonNode duracion, JsonNode viejo) {
		if (duracion.isString()) {
			String texto = duracion.asString();
			try {
				Duration d = Duration.parse(texto);
				long enteros = d.toDays();
				return (int) (d.minusDays(enteros).isZero() ? enteros : enteros + 1);
			} catch (DateTimeParseException e) {
				try {
					return Period.parse(texto).getDays(); // "P1W" no es un Duration válido
				} catch (DateTimeParseException e2) {
					// se usa el valor viejo
				}
			}
		}
		return viejo.isNumber() ? viejo.asInt() : null;
	}

	/** Los ids de Zipnova son numéricos: cualquier otra cosa nunca llega a la URL. */
	private static long numero(String id) {
		try {
			long numero = Long.parseLong(id);
			if (numero > 0) {
				return numero;
			}
		} catch (NumberFormatException e) {
			// cae al error
		}
		throw new ReglaNegocioException("Id de envío de Zipnova inválido");
	}

	private static String sinIds(String ruta) {
		return ruta.split("\\?")[0].replaceAll("/\\d+(?=/|$)", "/{id}");
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
