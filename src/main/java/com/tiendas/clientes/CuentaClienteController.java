package com.tiendas.clientes;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tiendas.comun.NoAutenticadoException;
import com.tiendas.comun.Textos;
import com.tiendas.config.InicioSesion;
import com.tiendas.config.IpCliente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Endpoints públicos de la cuenta del cliente: login, activación y "olvidé mi contraseña". El logout es el mismo
 * que el de administradores ({@code POST /api/v1/auth/logout}).
 */
@RestController
@RequestMapping("/api/v1/clientes")
@ClientesHabilitado
class CuentaClienteController {

	record LoginCliente(@NotBlank String email, @NotBlank String clave) {
	}

	/** Misma regla de contraseña que los administradores. */
	record NuevaClave(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String clave) {
	}

	record PedidoReseteo(@NotBlank @Email @Size(max = 160) String email) {
	}

	private final AutenticacionClientes autenticacion;
	private final CuentaClienteService cuentaService;
	private final LimitadorIntentosCliente limitador;
	private final InicioSesion inicioSesion;
	private final IpCliente ipCliente;

	CuentaClienteController(AutenticacionClientes autenticacion, CuentaClienteService cuentaService,
			LimitadorIntentosCliente limitador, InicioSesion inicioSesion, IpCliente ipCliente) {
		this.autenticacion = autenticacion;
		this.cuentaService = cuentaService;
		this.limitador = limitador;
		this.inicioSesion = inicioSesion;
		this.ipCliente = ipCliente;
	}

	/** Mismo 401 para email inexistente, contraseña incorrecta y cuenta pendiente o bloqueada. */
	@PostMapping("/login")
	Cliente login(@Valid @RequestBody LoginCliente login, HttpServletRequest request, HttpServletResponse response) {
		String email = Textos.minusculas(login.email());
		String ip = ipCliente.de(request);
		limitador.validarLogin(email, ip);

		Authentication sesion;
		try {
			sesion = autenticacion.autenticar(email, login.clave());
		} catch (AuthenticationException e) {
			limitador.registrarFalloLogin(email, ip);
			throw new NoAutenticadoException("Email o contraseña incorrectos");
		}
		limitador.registrarExitoLogin(email);
		inicioSesion.iniciar(sesion, request, response);
		return cuentaService.registrarAcceso(AutenticacionClientes.clienteId(sesion));
	}

	@GetMapping("/activacion/{token}")
	CuentaClienteService.Saludo saludoActivacion(@PathVariable String token) {
		return cuentaService.saludoActivacion(token);
	}

	@PostMapping("/activacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void activar(@Valid @RequestBody NuevaClave nueva) {
		cuentaService.activar(nueva.token(), nueva.clave());
	}

	/** Responde 204 exista o no la cuenta. */
	@PostMapping("/olvide-clave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void olvideClave(@Valid @RequestBody PedidoReseteo pedido, HttpServletRequest request) {
		String email = Textos.minusculas(pedido.email());
		limitador.registrarPedidoReseteo(email, ipCliente.de(request));
		cuentaService.pedirReseteo(email);
	}

	@GetMapping("/reseteo/{token}")
	CuentaClienteService.Saludo saludoReseteo(@PathVariable String token) {
		return cuentaService.saludoReseteo(token);
	}

	@PostMapping("/reseteo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetear(@Valid @RequestBody NuevaClave nueva) {
		cuentaService.resetear(nueva.token(), nueva.clave());
	}
}
