package com.tiendas.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deja guardada en la sesión una autenticación ya validada. Un solo lugar para todos los logins (administradores y
 * clientes): un cambio en cómo se abre la sesión no puede quedar aplicado en uno y no en el otro.
 */
@Component
public class InicioSesion {

	private final SecurityContextRepository securityContextRepository;
	private final CookieCsrfTokenRepository csrfTokenRepository;

	InicioSesion(SecurityContextRepository securityContextRepository, CookieCsrfTokenRepository csrfTokenRepository) {
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	public void iniciar(Authentication autenticacion, HttpServletRequest request, HttpServletResponse response) {
		// Nuevo id de sesión y nuevo token CSRF al autenticarse (evita session fixation)
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(autenticacion);
		SecurityContextHolder.setContext(contexto);
		securityContextRepository.saveContext(contexto, request, response);
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
	}
}
