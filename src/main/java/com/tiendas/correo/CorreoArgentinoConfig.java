package com.tiendas.correo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import tools.jackson.databind.json.JsonMapper;

/**
 * Crea el cliente de Correo Argentino si {@code correo-argentino.habilitada=true}. El simulado exige además el
 * perfil local (mismo doble candado que los pagos simulados): fuera de local la app no arranca si se pide.
 */
@Configuration
@ConditionalOnBooleanProperty("correo-argentino.habilitada")
class CorreoArgentinoConfig {

	@Bean
	CorreoArgentino correoArgentino(CorreoArgentinoProperties config, JsonMapper json, Environment entorno) {
		if (config.simulado()) {
			if (!entorno.acceptsProfiles(Profiles.of("local"))) {
				throw new IllegalStateException("correo-argentino.simulado solo se permite en el perfil local");
			}
			return new MiCorreoSimulado(config);
		}
		return new MiCorreoApi(config, json);
	}
}
