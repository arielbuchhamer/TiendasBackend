package com.tiendas.clientes;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.config.IpCliente;
import com.tiendas.pedidos.CheckoutService;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La cuenta del cliente con sesión (rol CLIENTE). Los datos los edita el administrador; el cliente solo cambia su
 * contraseña. Cada request vuelve a leer la cuenta: si se bloqueó, la sesión abierta responde 401.
 */
@RestController
@RequestMapping("/api/v1/clientes/yo")
@ClientesHabilitado
class MiCuentaController {

	record CambioClave(@NotBlank String claveActual, @NotBlank @Size(min = 10, max = 72) String claveNueva) {
	}

	private final CuentaClienteService cuentaService;
	private final PedidoService pedidoService;
	private final CheckoutService checkoutService;
	private final IpCliente ipCliente;

	MiCuentaController(CuentaClienteService cuentaService, PedidoService pedidoService,
			CheckoutService checkoutService, IpCliente ipCliente) {
		this.cuentaService = cuentaService;
		this.pedidoService = pedidoService;
		this.checkoutService = checkoutService;
		this.ipCliente = ipCliente;
	}

	@GetMapping
	Cliente yo(Authentication sesion) {
		return cuentaService.activo(AutenticacionClientes.clienteId(sesion));
	}

	@PutMapping("/clave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cambiarClave(Authentication sesion, @Valid @RequestBody CambioClave cambio) {
		cuentaService.cambiarClave(AutenticacionClientes.clienteId(sesion), cambio.claveActual(), cambio.claveNueva());
	}

	/**
	 * Checkout de un cliente con sesión: el mismo body que {@code POST /api/v1/checkout} pero sin {@code cliente}
	 * (si viene, se ignora): el comprador sale de la cuenta. Lleva CSRF, como todo request con sesión.
	 */
	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	Pedido checkout(Authentication sesion, @Valid @RequestBody Pedido pedido, HttpServletRequest request) {
		Long clienteId = AutenticacionClientes.clienteId(sesion);
		return checkoutService.iniciarConCuenta(pedido, clienteId, cuentaService.comprador(clienteId),
				ipCliente.de(request));
	}

	@GetMapping("/pedidos")
	Page<Pedido> pedidos(Authentication sesion, @PageableDefault(size = 20) Pageable pagina) {
		return pedidoService.listarDeCliente(clienteActivo(sesion), pagina);
	}

	@GetMapping("/pedidos/{codigo}")
	Pedido pedido(Authentication sesion, @PathVariable UUID codigo) {
		return pedidoService.obtenerDeCliente(codigo, clienteActivo(sesion));
	}

	private Long clienteActivo(Authentication sesion) {
		return cuentaService.activo(AutenticacionClientes.clienteId(sesion)).getId();
	}
}
