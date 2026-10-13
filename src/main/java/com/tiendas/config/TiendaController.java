package com.tiendas.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.correo.CorreoArgentinoProperties;
import com.tiendas.descuentos.DescuentosProperties;
import com.tiendas.envios.EnviosProperties;
import com.tiendas.facturacion.FacturacionProperties;

/**
 * Configuración pública de la tienda que necesita el frontend: nombre, moneda, opciones de entrega y
 * qué módulos opcionales están activos (el panel muestra u oculta sus secciones según {@code modulos}).
 */
@RestController
class TiendaController {

	record InfoTienda(String nombre, String moneda, TiendaProperties.Envio envio, Modulos modulos) {
	}

	/**
	 * Módulos opcionales. Cada valor sale de la misma propiedad que activa el módulo en el backend, así
	 * el panel nunca muestra una sección cuyos endpoints no existen (ni al revés).
	 */
	record Modulos(boolean facturacion, boolean descuentos, boolean envios, boolean correoArgentino) {
	}

	private final InfoTienda info;

	TiendaController(TiendaProperties tienda, FacturacionProperties facturacion, DescuentosProperties descuentos,
			EnviosProperties envios, CorreoArgentinoProperties correo) {
		this.info = new InfoTienda(tienda.nombre(), tienda.moneda(), tienda.envio(),
				new Modulos(facturacion.habilitada(), descuentos.habilitada(), envios.habilitada(),
						envios.habilitada() && correo.habilitada()));
	}

	@GetMapping("/api/v1/tienda")
	InfoTienda info() {
		return info;
	}
}
