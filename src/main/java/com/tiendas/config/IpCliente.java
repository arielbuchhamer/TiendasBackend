package com.tiendas.config;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP real del cliente detrás de proxies. Por defecto es la IP de la conexión (Tomcat ya descarta los proxies
 * internos de X-Forwarded-For). Con Cloudflare delante se configura su header, porque la conexión llega desde
 * IPs de Cloudflare y todos los clientes compartirían unas pocas IPs.
 */
@Component
public class IpCliente {

	// Solo caracteres de IPv4/IPv6: un header mal configurado no debe poder meter claves arbitrarias en el limitador
	private static final Pattern IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

	private final String header;

	public IpCliente(TiendaProperties propiedades) {
		String configurado = propiedades.proxy().headerIpCliente();
		this.header = configurado == null || configurado.isBlank() ? null : configurado;
	}

	public String de(HttpServletRequest request) {
		if (header != null) {
			String valor = request.getHeader(header);
			if (valor != null && IP.matcher(valor.trim()).matches()) {
				return valor.trim();
			}
		}
		return request.getRemoteAddr();
	}
}
