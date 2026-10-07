package com.tiendas.usuarios;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final CookieCsrfTokenRepository csrfTokenRepository;
	private final LimitadorIntentosLogin limitador;
	private final UsuarioService usuarioService;
	private final IpCliente ipCliente;

	public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
			CookieCsrfTokenRepository csrfTokenRepository, LimitadorIntentosLogin limitador, UsuarioService usuarioService,
			IpCliente ipCliente) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
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

		// Nuevo id de sesión y nuevo token CSRF al autenticarse (evita session fixation)
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(autenticacion);
		SecurityContextHolder.setContext(contexto);
		securityContextRepository.saveContext(contexto, request, response);
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

		return usuarioService.obtenerPorUsername(autenticacion.getName());
	}

	/** Usuario de la sesión actual; 401 si no hay sesión. */
	@GetMapping("/yo")
	public Usuario yo(Authentication autenticacion) {
		if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return usuarioService.obtenerPorUsername(autenticacion.getName());
	}
}
