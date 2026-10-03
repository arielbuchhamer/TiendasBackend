package com.tiendas.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración pública de la tienda que necesita el frontend (nombre, moneda, opciones de entrega). */
@RestController
class TiendaController {

	record InfoTienda(String nombre, String moneda, TiendaProperties.Envio envio) {
	}

	private final InfoTienda info;

	TiendaController(TiendaProperties tienda) {
		this.info = new InfoTienda(tienda.nombre(), tienda.moneda(), tienda.envio());
	}

	@GetMapping("/api/v1/tienda")
	InfoTienda info() {
		return info;
	}
}
