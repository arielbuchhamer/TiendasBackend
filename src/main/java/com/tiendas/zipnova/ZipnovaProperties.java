package com.tiendas.zipnova;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Integración con Zipnova (API de envíos v2): cotiza y da de alta envíos con varios transportes (Andreani, Correo
 * Argentino, OCA…) con las tarifas de Zipnova, sin contrato con cada uno.
 * <p>
 * Credenciales: en el panel de Zipnova, Configuración &gt; Integraciones &gt; Gestionar credenciales y webhooks
 * (un token y un secret por cuenta). El origen (desde dónde se despacha) es el que la cuenta tiene por defecto.
 *
 * @param url      base de la API: https://api.zipnova.com.ar/v2 (Argentina)
 * @param cuentaId id de la cuenta de Zipnova; opcional: si falta se usa la única cuenta de las credenciales
 * @param origenId id de la dirección de despacho; opcional: si falta se usa el origen por defecto de la cuenta
 * @param simulado solo perfil local: cotiza y responde con datos inventados, sin llamar a Zipnova
 * @param webhookSecreto parte secreta de la URL del webhook ({@code /api/v1/webhooks/zipnova/{secreto}}), al menos
 *                       32 caracteres; vacío = los avisos de Zipnova se rechazan
 */
@ConfigurationProperties(prefix = "zipnova")
public record ZipnovaProperties(
		@DefaultValue("false") boolean habilitada,
		@DefaultValue("https://api.zipnova.com.ar/v2") String url,
		String apiToken,
		String apiSecret,
		Long cuentaId,
		Long origenId,
		@DefaultValue("false") boolean simulado,
		String webhookSecreto) {

	public ZipnovaProperties {
		if (habilitada && !simulado) {
			requerir(apiToken, "zipnova.api-token");
			requerir(apiSecret, "zipnova.api-secret");
		}
	}

	private static void requerir(String valor, String propiedad) {
		if (!StringUtils.hasText(valor)) {
			throw new IllegalStateException("Zipnova habilitado: falta configurar " + propiedad);
		}
	}
}
