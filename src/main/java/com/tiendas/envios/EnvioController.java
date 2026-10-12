package com.tiendas.envios;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@EnviosHabilitada
class EnvioController {

	/** Carrito para cotizar (variante y cantidad); el subtotal lo calcula el servidor. */
	record SolicitudCotizacion(@Size(max = 12) String codigoPostal, @Valid @Size(max = 50) List<Linea> items) {
	}

	record Linea(@NotNull Long varianteId, @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {
	}

	private final EnvioService envioService;

	EnvioController(EnvioService envioService) {
		this.envioService = envioService;
	}

	/** Retiro, si hay envío a domicilio y desde qué monto el envío es gratis. */
	@GetMapping("/envios")
	EnvioService.InfoEnvios info() {
		return envioService.info();
	}

	/** Opciones de entrega para un código postal y un carrito (lo usan el artículo, el carrito y el checkout). */
	@PostMapping("/envios/cotizar")
	EnvioService.Cotizacion cotizar(@Valid @RequestBody SolicitudCotizacion solicitud) {
		Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();
		if (solicitud.items() != null) {
			solicitud.items().forEach(l -> cantidades.merge(l.varianteId(), l.cantidad(), BigDecimal::add));
		}
		return envioService.cotizar(solicitud.codigoPostal(), cantidades);
	}

	// ─── Panel ──────────────────────────────────────────────────────────

	@GetMapping("/admin/envios/ajustes")
	AjustesEnvio ajustes() {
		return envioService.ajustes();
	}

	@PutMapping("/admin/envios/ajustes")
	AjustesEnvio guardarAjustes(@Valid @RequestBody AjustesEnvio ajustes) {
		return envioService.guardarAjustes(ajustes);
	}

	@GetMapping("/admin/envios/zonas")
	List<ZonaEnvio> zonas() {
		return envioService.zonas();
	}

	@PostMapping("/admin/envios/zonas")
	@ResponseStatus(HttpStatus.CREATED)
	ZonaEnvio crearZona(@Valid @RequestBody ZonaEnvio zona) {
		return envioService.crearZona(zona);
	}

	@PutMapping("/admin/envios/zonas/{id}")
	ZonaEnvio actualizarZona(@PathVariable Long id, @Valid @RequestBody ZonaEnvio zona) {
		return envioService.actualizarZona(id, zona);
	}

	@DeleteMapping("/admin/envios/zonas/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void eliminarZona(@PathVariable Long id) {
		envioService.eliminarZona(id);
	}
}
