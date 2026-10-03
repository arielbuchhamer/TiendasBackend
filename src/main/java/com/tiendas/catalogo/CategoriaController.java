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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CategoriaController {

	private final CategoriaService categoriaService;

	public CategoriaController(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	@GetMapping("/categorias")
	public List<Categoria> listar(@RequestParam(required = false) Long rubroId) {
		return categoriaService.listar(rubroId);
	}

	@GetMapping("/categorias/{id}")
	public Categoria obtener(@PathVariable Long id) {
		return categoriaService.obtener(id);
	}

	@PostMapping("/admin/categorias")
	@ResponseStatus(HttpStatus.CREATED)
	public Categoria crear(@Valid @RequestBody Categoria categoria) {
		return categoriaService.crear(categoria);
	}

	@PutMapping("/admin/categorias/{id}")
	public Categoria actualizar(@PathVariable Long id, @Valid @RequestBody Categoria categoria) {
		return categoriaService.actualizar(id, categoria);
	}

	@DeleteMapping("/admin/categorias/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		categoriaService.eliminar(id);
	}
}
