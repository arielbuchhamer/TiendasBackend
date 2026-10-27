package com.tiendas.personalizacion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@PersonalizacionHabilitada
class PersonalizacionController {

	private final PersonalizacionService personalizacionService;

	PersonalizacionController(PersonalizacionService personalizacionService) {
		this.personalizacionService = personalizacionService;
	}

	/** Público: la página del producto muestra el nombre y el precio. */
	@GetMapping("/personalizacion")
	AjustesPersonalizacion ajustes() {
		return personalizacionService.ajustes();
	}

	@PutMapping("/admin/personalizacion")
	AjustesPersonalizacion guardar(@Valid @RequestBody AjustesPersonalizacion ajustes) {
		return personalizacionService.guardar(ajustes);
	}
}
