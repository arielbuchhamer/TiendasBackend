package com.tiendas.facturacion;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/** Consultas operativas a ARCA (vía AFRelay) para diagnóstico desde el panel. Siempre con el CUIT de la tienda. */
@RestController
@RequestMapping("/api/v1/admin/facturacion")
@FacturacionHabilitada
class AfRelayController {

	private final AfRelayClient afRelay;
	private final long cuit;

	AfRelayController(AfRelayClient afRelay, FacturacionProperties facturacion) {
		this.afRelay = afRelay;
		this.cuit = Long.parseLong(facturacion.emisor().cuit().replaceAll("\\D", ""));
	}

	@GetMapping("/estado")
	Map<String, String> estado() {
		return Map.of("afrelay", afRelay.estado());
	}

	@PostMapping("/ticket-acceso")
	JsonNode renovarTicketAcceso() {
		return afRelay.renovarTicketAcceso();
	}

	@GetMapping("/puntos-venta")
	JsonNode puntosVenta() {
		return afRelay.obtenerPuntosVenta(cuit);
	}

	@GetMapping("/ultimo-autorizado")
	JsonNode ultimoAutorizado(@RequestParam int puntoVenta, @RequestParam TipoComprobante tipo) {
		return afRelay.obtenerUltimoAutorizado(cuit, puntoVenta, tipo);
	}

	@GetMapping("/comprobante-arca")
	JsonNode consultar(@RequestParam int puntoVenta, @RequestParam TipoComprobante tipo, @RequestParam long numero) {
		return afRelay.consultarComprobante(cuit, puntoVenta, tipo, numero);
	}
}
