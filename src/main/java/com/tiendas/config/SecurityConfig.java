package com.tiendas.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Reglas de acceso:
 * <ul>
 * <li>{@code /api/v1/admin/**}: solo usuarios con rol ADMIN (sesión por cookie).</li>
 * <li>Lectura del catálogo, checkout, webhook y login: públicos.</li>
 * <li>Cualquier otra ruta: denegada (lista blanca explícita).</li>
 * </ul>
 * CSRF activo para todo request autenticado que modifica datos: el frontend lee la cookie XSRF-TOKEN y la
 * reenvía en el header X-XSRF-TOKEN. Se excluyen el webhook (se valida con firma HMAC) y el checkout
 * (es anónimo: no hay sesión que un sitio malicioso pueda aprovechar).
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filtrosSeguridad(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf
						.spa()
						.csrfTokenRepository(csrfTokenRepository)
						.ignoringRequestMatchers("/api/v1/webhooks/**", "/api/v1/checkout", "/api/v1/checkout/cupon"))
				.securityContext(contexto -> contexto.securityContextRepository(securityContextRepository))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers(GET,
								"/api/v1/tienda",
								"/api/v1/rubros/**",
								"/api/v1/categorias/**",
								"/api/v1/productos/**",
								"/api/v1/imagenes/**",
								"/api/v1/comprobantes/*/pdf").permitAll()
						.requestMatchers(POST, "/api/v1/checkout", "/api/v1/checkout/cupon", "/api/v1/webhooks/mercadopago").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
						.anyRequest().denyAll())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(logout -> logout
						.logoutUrl("/api/v1/auth/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'")));
		return http.build();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(TiendaProperties tienda) {
		CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
		TiendaProperties.Cookies cookies = tienda.cookies();
		repositorio.setCookieCustomizer(cookie -> {
			cookie.path("/").secure(cookies.secure()).sameSite(cookies.sameSite());
			if (StringUtils.hasText(cookies.dominio())) {
				cookie.domain(cookies.dominio());
			}
		});
		return repositorio;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(TiendaProperties tienda) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(tienda.corsOrigenes());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
		cors.setAllowCredentials(true);
		cors.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(userDetailsService);
		proveedor.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(proveedor);
	}
}
