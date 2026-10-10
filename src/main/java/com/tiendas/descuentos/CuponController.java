package com.tiendas.descuentos;

import java.util.List;

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

/**
 * ABM de cupones para el panel. El cliente no usa estos endpoints: valida su código con
 * {@code POST /api/v1/checkout/cupon} y lo envía en el checkout.
 */
@RestController
@RequestMapping("/api/v1/admin/cupones")
@DescuentosHabilitada
class CuponController {

	private final CuponService cuponService;

	CuponController(CuponService cuponService) {
		this.cuponService = cuponService;
	}

	/** Todos los cupones, más nuevos primero, con sus usos y lo vendido y descontado con cada uno. */
	@GetMapping
	List<Cupon> listar() {
		return cuponService.listar();
	}

	@GetMapping("/{id}")
	Cupon obtener(@PathVariable Long id) {
		return cuponService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Cupon crear(@Valid @RequestBody Cupon cupon) {
		return cuponService.crear(cupon);
	}

	@PutMapping("/{id}")
	Cupon actualizar(@PathVariable Long id, @Valid @RequestBody Cupon cupon) {
		return cuponService.actualizar(id, cupon);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void eliminar(@PathVariable Long id) {
		cuponService.eliminar(id);
	}
}
