package com.tiendas.clientes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Clientes desde el panel: alta, edición, bloqueo e invitación. No hay registro público. */
@RestController
@RequestMapping("/api/v1/admin/clientes")
@ClientesHabilitado
class ClienteController {

	record CambioEstado(@NotNull EstadoCliente estado) {
	}

	private final ClienteService clienteService;

	ClienteController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping
	Page<Cliente> listar(@RequestParam(required = false) EstadoCliente estado,
			@RequestParam(required = false) String texto, @PageableDefault(size = 20) Pageable pagina) {
		return clienteService.listar(new FiltroClientes(estado, texto), pagina);
	}

	@GetMapping("/{id}")
	Cliente obtener(@PathVariable Long id) {
		return clienteService.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Cliente crear(@Valid @RequestBody Cliente cliente) {
		return clienteService.crear(cliente);
	}

	@PutMapping("/{id}")
	Cliente actualizar(@PathVariable Long id, @Valid @RequestBody Cliente cliente) {
		return clienteService.actualizar(id, cliente);
	}

	@PatchMapping("/{id}/estado")
	Cliente cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstado cambio) {
		return clienteService.cambiarEstado(id, cambio.estado());
	}

	/** Genera el link de activación y le manda el email al cliente. */
	@PostMapping("/{id}/invitacion")
	Cliente enviarInvitacion(@PathVariable Long id) {
		return clienteService.enviarInvitacion(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void eliminar(@PathVariable Long id) {
		clienteService.eliminar(id);
	}
}
