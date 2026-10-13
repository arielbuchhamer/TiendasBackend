package com.tiendas.correo;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.pedidos.Pedido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Acciones de Correo Argentino sobre una venta, desde el panel. */
@RestController
@RequestMapping("/api/v1/admin/pedidos/{id}")
@ConditionalOnBooleanProperty("correo-argentino.habilitada")
class CorreoController {

	record CodigoSeguimiento(@Size(max = 60) String codigo) {
	}

	private final CorreoEnvios correoEnvios;

	CorreoController(CorreoEnvios correoEnvios) {
		this.correoEnvios = correoEnvios;
	}

	/** Da de alta el envío en MiCorreo: queda en "Mis envíos" para pagarlo e imprimir el rótulo. */
	@PostMapping("/correo")
	Pedido darDeAlta(@PathVariable Long id) {
		return correoEnvios.darDeAlta(id);
	}

	@PutMapping("/seguimiento")
	Pedido guardarSeguimiento(@PathVariable Long id, @Valid @RequestBody CodigoSeguimiento seguimiento) {
		return correoEnvios.guardarSeguimiento(id, seguimiento.codigo());
	}

	@GetMapping("/seguimiento")
	List<CorreoArgentino.EventoSeguimiento> seguimiento(@PathVariable Long id) {
		return correoEnvios.seguimiento(id);
	}
}
