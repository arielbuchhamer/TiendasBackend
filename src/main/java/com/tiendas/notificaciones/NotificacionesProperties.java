package com.tiendas.notificaciones;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Mails de la tienda: aviso de venta al vendedor y confirmación al cliente. El servidor SMTP se configura
 * aparte, con las propiedades estándar {@code spring.mail.*} (variables MAIL_*).
 *
 * @param remitente      quién figura como remitente, ej. {@code "Mi Tienda <ventas@mitienda.com.ar>"}
 * @param avisosVendedor mails que reciben el aviso de cada venta
 * @param mailCliente    si se le manda la confirmación de compra al cliente
 * @param logo           logo para el encabezado, como recurso de Spring ({@code https://...}, {@code file:...});
 *                       se adjunta embebido para que se vea aunque el cliente bloquee imágenes externas
 * @param colorAcento    color de marca (botones y detalles) en hexadecimal
 * @param whatsapp       número de WhatsApp de la tienda con código de país, solo dígitos (ej. 5493445623330)
 * @param proximosPasos  qué tiene que hacer el cliente después de comprar (se muestra destacado en su mail)
 */
@ConfigurationProperties(prefix = "notificaciones")
public record NotificacionesProperties(
		@DefaultValue("false") boolean habilitada,
		String remitente,
		@DefaultValue List<String> avisosVendedor,
		@DefaultValue("true") boolean mailCliente,
		String logo,
		@DefaultValue("#1c1917") String colorAcento,
		String whatsapp,
		@DefaultValue("Te vamos a escribir para coordinar la entrega.") String proximosPasos) {

	public NotificacionesProperties {
		if (habilitada && !StringUtils.hasText(remitente)) {
			throw new IllegalStateException("Notificaciones habilitadas: falta configurar notificaciones.remitente");
		}
		avisosVendedor = avisosVendedor.stream().filter(StringUtils::hasText).map(String::trim).toList();
	}
}
