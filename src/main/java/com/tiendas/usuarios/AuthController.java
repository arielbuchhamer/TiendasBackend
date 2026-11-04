package com.tiendas.usuarios;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tiendas.config.InicioSesion;
import com.tiendas.config.IpCliente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Login por sesión (cookie HttpOnly). El logout lo resuelve Spring Security en POST /api/v1/auth/logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	private static final String ROL_ADMIN = "ROLE_" + Rol.ADMIN.name();

	private final AuthenticationManager authenticationManager;
	private final InicioSesion inicioSesion;
	private final LimitadorIntentosLogin limitador;
	private final UsuarioService usuarioService;
	private final IpCliente ipCliente;

	public AuthController(AuthenticationManager authenticationManager, InicioSesion inicioSesion,
			LimitadorIntentosLogin limitador, UsuarioService usuarioService, IpCliente ipCliente) {
		this.authenticationManager = authenticationManager;
		this.inicioSesion = inicioSesion;
		this.limitador = limitador;
		this.usuarioService = usuarioService;
		this.ipCliente = ipCliente;
	}

	@PostMapping("/login")
	public Usuario login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
		String ip = ipCliente.de(request);
		if (limitador.estaBloqueado(login.username(), ip)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.");
		}

		Authentication autenticacion;
		try {
			autenticacion = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
		} catch (AuthenticationException e) {
			limitador.registrarFallo(login.username(), ip);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o clave incorrectos");
		}
		limitador.registrarExito(login.username());

		inicioSesion.iniciar(autenticacion, request, response);

		return usuarioService.obtenerPorUsername(autenticacion.getName());
	}

	/**
	 * Usuario de la sesión actual; 401 si no hay sesión de administrador. Una sesión de cliente (módulo de clientes)
	 * también da 401: el panel la trata como "sin sesión" y manda al login de administrador.
	 */
	@GetMapping("/yo")
	public Usuario yo(Authentication autenticacion) {
		if (autenticacion == null || autenticacion.getAuthorities().stream()
				.noneMatch(permiso -> ROL_ADMIN.equals(permiso.getAuthority()))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return usuarioService.obtenerPorUsername(autenticacion.getName());
	}
}
