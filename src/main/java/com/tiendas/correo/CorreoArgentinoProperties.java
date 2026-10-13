package com.tiendas.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Integración con la API de MiCorreo (Correo Argentino). Las credenciales de la API se piden al Correo, una por
 * ambiente (TEST y producción); el número de cliente figura en MiCorreo, en Mi Perfil > Datos de facturación.
 *
 * @param url               base de la API: https://apitest.correoargentino.com.ar/micorreo/v1 (TEST) o
 *                          https://api.correoargentino.com.ar/micorreo/v1 (producción)
 * @param codigoPostalOrigen código postal desde donde se despacha (el del domicilio de remitente de MiCorreo)
 * @param simulado          solo perfil local: cotiza y responde con datos inventados, sin llamar al Correo
 */
@ConfigurationProperties(prefix = "correo-argentino")
public record CorreoArgentinoProperties(
		@DefaultValue("false") boolean habilitada,
		@DefaultValue("https://api.correoargentino.com.ar/micorreo/v1") String url,
		String usuario,
		String clave,
		String numeroCliente,
		String codigoPostalOrigen,
		@DefaultValue("false") boolean simulado) {

	public CorreoArgentinoProperties {
		if (habilitada && !simulado) {
			requerir(usuario, "correo-argentino.usuario");
			requerir(clave, "correo-argentino.clave");
			requerir(numeroCliente, "correo-argentino.numero-cliente");
		}
		if (habilitada) {
			requerir(codigoPostalOrigen, "correo-argentino.codigo-postal-origen");
		}
	}

	private static void requerir(String valor, String propiedad) {
		if (!StringUtils.hasText(valor)) {
			throw new IllegalStateException("Correo Argentino habilitado: falta configurar " + propiedad);
		}
	}
}
