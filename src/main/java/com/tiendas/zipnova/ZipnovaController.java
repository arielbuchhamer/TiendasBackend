package com.tiendas.zipnova;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.pedidos.Pedido;

/** Acciones de Zipnova sobre una venta, desde el panel. */
@RestController
@RequestMapping("/api/v1/admin/pedidos/{id}/zipnova")
@ConditionalOnBooleanProperty("zipnova.habilitada")
class ZipnovaController {

	private final ZipnovaEnvios zipnovaEnvios;

	ZipnovaController(ZipnovaEnvios zipnovaEnvios) {
		this.zipnovaEnvios = zipnovaEnvios;
	}

	/** Crea el envío en Zipnova con el transporte y la forma de entrega que eligió el cliente. */
	@PostMapping
	Pedido darDeAlta(@PathVariable Long id) {
		return zipnovaEnvios.darDeAlta(id);
	}

	/** Etiqueta en PDF para pegar en el paquete. */
	@GetMapping("/etiqueta")
	ResponseEntity<byte[]> etiqueta(@PathVariable Long id) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("etiqueta-venta-" + id + ".pdf").build().toString())
				.body(zipnovaEnvios.etiqueta(id));
	}

	/** Guía de despacho en PDF (solo algunos transportes la usan). */
	@GetMapping("/guia")
	ResponseEntity<byte[]> guia(@PathVariable Long id) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("guia-venta-" + id + ".pdf").build().toString())
				.body(zipnovaEnvios.guia(id));
	}

	/** Dónde llevar el paquete: puntos del transporte cerca del origen. */
	@GetMapping("/despacho")
	ZipnovaEnvios.Despacho despacho(@PathVariable Long id) {
		return zipnovaEnvios.despacho(id);
	}

	/** Cancela el envío (o pide el rescate si ya se despachó). */
	@PostMapping("/cancelar")
	Pedido cancelar(@PathVariable Long id) {
		return zipnovaEnvios.cancelar(id);
	}

	/** Consulta el envío en Zipnova: devuelve el historial y la venta con el estado actualizado. */
	@PostMapping("/seguimiento")
	ZipnovaEnvios.Seguimiento seguimiento(@PathVariable Long id) {
		return zipnovaEnvios.seguimiento(id);
	}
}
