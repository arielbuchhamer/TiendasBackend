package com.tiendas.zipnova;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos de la cuenta de Zipnova para el panel. */
@RestController
@ConditionalOnBooleanProperty("zipnova.habilitada")
class ZipnovaCuentaController {

	private final ZipnovaEnvios zipnovaEnvios;

	ZipnovaCuentaController(ZipnovaEnvios zipnovaEnvios) {
		this.zipnovaEnvios = zipnovaEnvios;
	}

	/** Saldo de la cuenta: los envíos se cobran de acá y sin saldo (ni crédito) no se pueden crear. */
	@GetMapping("/api/v1/admin/zipnova/saldo")
	Zipnova.Saldo saldo() {
		return zipnovaEnvios.saldo();
	}
}
