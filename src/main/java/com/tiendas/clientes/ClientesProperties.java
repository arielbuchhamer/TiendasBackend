package com.tiendas.clientes;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Módulo opcional de cuentas de cliente: el administrador da de alta a los clientes, los invita por email a elegir
 * su contraseña y, con el módulo activo, para comprar hace falta iniciar sesión.
 *
 * @param horasInvitacion cuánto dura el link de activación que recibe el cliente
 * @param minutosReseteo  cuánto dura el link para elegir una contraseña nueva
 * @param rutaActivacion  ruta del frontend que recibe el token de activación ({@code {ruta}/{token}})
 * @param rutaReseteo     ruta del frontend que recibe el token para cambiar la contraseña
 */
@ConfigurationProperties(prefix = "clientes")
public record ClientesProperties(
		@DefaultValue("false") boolean habilitada,
		@DefaultValue("72") int horasInvitacion,
		@DefaultValue("60") int minutosReseteo,
		@DefaultValue("/account/activate") String rutaActivacion,
		@DefaultValue("/account/reset") String rutaReseteo) {

	public ClientesProperties {
		if (habilitada) {
			if (horasInvitacion < 1 || minutosReseteo < 1) {
				throw new IllegalStateException(
						"Clientes habilitado: clientes.horas-invitacion y clientes.minutos-reseteo deben ser mayores a 0");
			}
			if (!rutaActivacion.startsWith("/") || !rutaReseteo.startsWith("/")) {
				throw new IllegalStateException(
						"Clientes habilitado: clientes.ruta-activacion y clientes.ruta-reseteo deben empezar con /");
			}
		}
	}

	Duration vigenciaInvitacion() {
		return Duration.ofHours(horasInvitacion);
	}

	Duration vigenciaReseteo() {
		return Duration.ofMinutes(minutosReseteo);
	}
}
