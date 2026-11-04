package com.tiendas.clientes;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Verifica email y contraseña de un cliente. El {@link AuthenticationManager} es propio y no se registra como bean:
 * el de administradores sigue siendo el único del contexto, así un cliente nunca puede autenticarse como
 * administrador (ni al revés) y no hace falta desambiguar beans.
 * <p>
 * El nombre de la sesión es el id del cliente (no el email): si el administrador le cambia el email, la sesión sigue
 * apuntando a la misma cuenta.
 */
@Component
@ClientesHabilitado
class AutenticacionClientes {

	private final AuthenticationManager manager;

	AutenticacionClientes(ClienteRepository clienteRepository, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(email -> cargar(clienteRepository, email));
		proveedor.setPasswordEncoder(passwordEncoder);
		this.manager = new ProviderManager(proveedor);
	}

	/**
	 * @param email ya normalizado
	 * @throws AuthenticationException si el email no existe, la contraseña no coincide o la cuenta no está activa
	 */
	Authentication autenticar(String email, String clave) {
		return manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, clave));
	}

	/** Id del cliente de una sesión autenticada por esta clase. */
	static Long clienteId(Authentication autenticacion) {
		return Long.valueOf(autenticacion.getName());
	}

	// Una cuenta pendiente o bloqueada responde igual que un email inexistente: el proveedor igual compara contra un
	// hash ficticio, así el tiempo de respuesta tampoco revela qué cuentas existen
	private static UserDetails cargar(ClienteRepository clienteRepository, String email) {
		Cliente cliente = clienteRepository.findByEmail(email)
				.filter(Cliente::estaActivo)
				.orElseThrow(() -> new UsernameNotFoundException("Cliente inexistente o inactivo"));
		return User.withUsername(String.valueOf(cliente.getId()))
				.password(cliente.getPasswordHash())
				.roles(Cliente.ROL)
				.build();
	}
}
