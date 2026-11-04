package com.tiendas.clientes;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tiendas.comun.NoAutenticadoException;
import com.tiendas.comun.NoEncontradoException;
import com.tiendas.comun.ReglaNegocioException;

/**
 * Lo que hace el propio cliente con su cuenta: activarla, recuperar la contraseña y consultar sus datos.
 * El ABM del panel está en {@link ClienteService}.
 */
@Service
@ClientesHabilitado
@Transactional(readOnly = true)
class CuentaClienteService {

	/** Lo que muestra el front antes de elegir la contraseña ("Hola, Juan"). */
	record Saludo(String nombre, String email) {
	}

	private final ClienteRepository clienteRepository;
	private final TokensCliente tokens;
	private final PasswordEncoder passwordEncoder;
	private final ClientesProperties propiedades;
	private final ApplicationEventPublisher eventos;

	CuentaClienteService(ClienteRepository clienteRepository, TokensCliente tokens, PasswordEncoder passwordEncoder,
			ClientesProperties propiedades, ApplicationEventPublisher eventos) {
		this.clienteRepository = clienteRepository;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.propiedades = propiedades;
		this.eventos = eventos;
	}

	@Transactional
	Cliente registrarAcceso(Long clienteId) {
		Cliente cliente = activo(clienteId);
		cliente.setUltimoAcceso(Instant.now());
		return cliente;
	}

	/**
	 * Cliente de la sesión, releído de la base en cada request: si el administrador lo bloqueó, la sesión que tenía
	 * abierta deja de servir.
	 */
	Cliente activo(Long clienteId) {
		return clienteRepository.findById(clienteId)
				.filter(Cliente::estaActivo)
				.orElseThrow(() -> new NoAutenticadoException("Tu sesión ya no es válida. Iniciá sesión de nuevo."));
	}

	@Transactional
	void cambiarClave(Long clienteId, String actual, String nueva) {
		Cliente cliente = activo(clienteId);
		if (!passwordEncoder.matches(actual, cliente.getPasswordHash())) {
			throw new ReglaNegocioException("La contraseña actual no es correcta");
		}
		cliente.setPasswordHash(passwordEncoder.encode(nueva));
	}

	// ─── Activación (primera contraseña) ────────────────────────────────

	Saludo saludoActivacion(String token) {
		return saludo(clienteDelLink(tokens.clienteDe(token, TipoTokenCliente.ACTIVACION), EstadoCliente.PENDIENTE));
	}

	@Transactional
	void activar(String token, String clave) {
		Cliente cliente = clienteDelLink(tokens.consumir(token, TipoTokenCliente.ACTIVACION), EstadoCliente.PENDIENTE);
		cliente.setPasswordHash(passwordEncoder.encode(clave));
		cliente.setEstado(EstadoCliente.ACTIVO);
	}

	// ─── Olvidé mi contraseña ───────────────────────────────────────────

	/**
	 * Manda el link solo a cuentas activas, pero no avisa si el email no existe o la cuenta no está activa: el
	 * formulario responde siempre lo mismo, así no sirve para averiguar quién es cliente de la tienda.
	 */
	@Transactional
	void pedirReseteo(String email) {
		clienteRepository.findByEmail(email).filter(Cliente::estaActivo).ifPresent(cliente -> {
			String token = tokens.generar(cliente.getId(), TipoTokenCliente.RESETEO);
			eventos.publishEvent(new ReseteoClaveCliente(cliente.getId(), cliente.getEmail(), cliente.getNombre(),
					tokens.link(TipoTokenCliente.RESETEO, token), propiedades.vigenciaReseteo()));
		});
	}

	Saludo saludoReseteo(String token) {
		return saludo(clienteDelLink(tokens.clienteDe(token, TipoTokenCliente.RESETEO), EstadoCliente.ACTIVO));
	}

	@Transactional
	void resetear(String token, String clave) {
		Cliente cliente = clienteDelLink(tokens.consumir(token, TipoTokenCliente.RESETEO), EstadoCliente.ACTIVO);
		cliente.setPasswordHash(passwordEncoder.encode(clave));
	}

	// Un link de una cuenta que cambió de estado (ej. se bloqueó después de enviarlo) ya no sirve
	private Cliente clienteDelLink(Long clienteId, EstadoCliente estadoEsperado) {
		return clienteRepository.findById(clienteId)
				.filter(cliente -> cliente.getEstado() == estadoEsperado)
				.orElseThrow(() -> new NoEncontradoException("El link no es válido o ya venció. Pedí uno nuevo."));
	}

	private static Saludo saludo(Cliente cliente) {
		return new Saludo(cliente.getNombre(), cliente.getEmail());
	}
}
