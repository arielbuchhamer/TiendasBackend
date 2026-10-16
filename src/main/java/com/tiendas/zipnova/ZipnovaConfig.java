package com.tiendas.zipnova;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import tools.jackson.databind.json.JsonMapper;

/**
 * Crea el cliente de Zipnova si {@code zipnova.habilitada=true}. El simulado exige además el perfil local (mismo
 * doble candado que los pagos simulados): fuera de local la app no arranca si se pide.
 */
@Configuration
@ConditionalOnBooleanProperty("zipnova.habilitada")
class ZipnovaConfig {

	@Bean
	Zipnova zipnova(ZipnovaProperties config, JsonMapper json, Environment entorno) {
		if (config.simulado()) {
			if (!entorno.acceptsProfiles(Profiles.of("local"))) {
				throw new IllegalStateException("zipnova.simulado solo se permite en el perfil local");
			}
			return new ZipnovaSimulado();
		}
		return new ZipnovaApi(config, json);
	}
}
