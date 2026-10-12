package com.tiendas.emails;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuración de los emails de la tienda (enviados con Resend). Si el módulo está habilitado y falta un dato
 * obligatorio, la aplicación no arranca.
 *
 * @param remitente  "Nombre &lt;direccion&gt;"; el dominio tiene que estar verificado en Resend
 * @param admin      destinatarios de los avisos de venta
 * @param responderA a dónde llegan las respuestas del cliente al email de confirmación; vacío = primer admin
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
		@DefaultValue("false") boolean habilitada,
		String remitente,
		@DefaultValue List<String> admin,
		String responderA,
		@DefaultValue Resend resend) {

	public EmailProperties {
		if (habilitada) {
			requerir(remitente, "email.remitente");
			requerir(resend.apiKey(), "email.resend.api-key");
			if (admin.stream().noneMatch(StringUtils::hasText)) {
				throw new IllegalStateException("Email habilitado: falta configurar email.admin");
			}
		}
	}

	/** Dirección para responder al cliente: la configurada o, si no hay, la del primer administrador. */
	public String responderACliente() {
		return StringUtils.hasText(responderA) ? responderA : admin.getFirst();
	}

	/** @param url configurable para apuntar a un servidor de prueba en desarrollo */
	public record Resend(String apiKey, @DefaultValue("https://api.resend.com") String url) {
	}

	private static void requerir(String valor, String propiedad) {
		if (!StringUtils.hasText(valor)) {
			throw new IllegalStateException("Email habilitado: falta configurar " + propiedad);
		}
	}
}
