package com.tiendas.usuarios;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/admin/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	public List<Usuario> listar() {
		return usuarioService.listar();
	}

	@GetMapping("/{id}")
	public Usuario obtener(@PathVariable Long id) {
		return usuarioService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Usuario crear(@Valid @RequestBody Usuario usuario) {
		return usuarioService.crear(usuario);
	}

	@PutMapping("/{id}")
	public Usuario actualizar(@PathVariable Long id, @Valid @RequestBody Usuario usuario, Authentication autenticacion) {
		return usuarioService.actualizar(id, usuario, autenticacion.getName());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id, Authentication autenticacion) {
		usuarioService.eliminar(id, autenticacion.getName());
	}
}
