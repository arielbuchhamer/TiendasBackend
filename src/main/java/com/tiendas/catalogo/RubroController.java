package com.tiendas.catalogo;

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

@RestController
@RequestMapping("/api/v1")
public class RubroController {

	private final RubroService rubroService;

	public RubroController(RubroService rubroService) {
		this.rubroService = rubroService;
	}

	@GetMapping("/rubros")
	public List<Rubro> listar() {
		return rubroService.listar();
	}

	@GetMapping("/rubros/{id}")
	public Rubro obtener(@PathVariable Long id) {
		return rubroService.obtener(id);
	}

	@PostMapping("/admin/rubros")
	@ResponseStatus(HttpStatus.CREATED)
	public Rubro crear(@Valid @RequestBody Rubro rubro) {
		return rubroService.crear(rubro);
	}

	@PutMapping("/admin/rubros/{id}")
	public Rubro actualizar(@PathVariable Long id, @Valid @RequestBody Rubro rubro) {
		return rubroService.actualizar(id, rubro);
	}

	@DeleteMapping("/admin/rubros/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		rubroService.eliminar(id);
	}
}
