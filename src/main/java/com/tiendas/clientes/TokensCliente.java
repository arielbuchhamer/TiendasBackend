package com.tiendas.clientes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.tiendas.comun.NoEncontradoException;
import com.tiendas.config.TiendaProperties;

/**
 * Links de un solo uso que recibe el cliente por email (activación y cambio de contraseña). En la base se guarda
 * solo el SHA-256 del token: quien lea la tabla no puede usar los links. Un link que no existe, ya se usó o venció
 * da el mismo error, así no se puede saber cuál de los tres casos es.
 */
@Component
@ClientesHabilitado
class TokensCliente {

	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final int BYTES_TOKEN = 32;
	// Un token generado acá tiene 43 caracteres; se corta antes de hashear cualquier cosa más larga
	private static final int LARGO_MAXIMO = 100;

	private final ClienteTokenRepository repository;
	private final ClientesProperties propiedades;
	private final TiendaProperties tienda;

	TokensCliente(ClienteTokenRepository repository, ClientesProperties propiedades, TiendaProperties tienda) {
		this.repository = repository;
		this.propiedades = propiedades;
		this.tienda = tienda;
	}

	/** Crea un link nuevo e invalida los anteriores del mismo tipo: solo vale el último que se envió. */
	String generar(Long clienteId, TipoTokenCliente tipo) {
		Instant ahora = Instant.now();
		repository.invalidarAnteriores(clienteId, tipo, ahora);

		byte[] bytes = new byte[BYTES_TOKEN];
		ALEATORIO.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		ClienteToken registro = new ClienteToken();
		registro.setClienteId(clienteId);
		registro.setTipo(tipo);
		registro.setHash(hash(token));
		registro.setVenceEn(ahora.plus(tipo.vigencia(propiedades)));
		repository.save(registro);
		return token;
	}

	/** URL del frontend que recibe el token ({@code {frontend}{ruta}/{token}}). */
	String link(TipoTokenCliente tipo, String token) {
		String ruta = switch (tipo) {
			case ACTIVACION -> propiedades.rutaActivacion();
			case RESETEO -> propiedades.rutaReseteo();
		};
		return tienda.frontendUrl() + ruta + "/" + token;
	}

	/** Id del cliente de un link vigente, sin usarlo (para mostrar "Hola, Juan" antes de elegir la contraseña). */
	Long clienteDe(String token, TipoTokenCliente tipo) {
		return vigente(token, tipo).getClienteId();
	}

	/**
	 * Marca el link como usado y devuelve el id de su cliente. El UPDATE condicional hace que dos requests
	 * simultáneos con el mismo link no puedan usarlo los dos.
	 */
	Long consumir(String token, TipoTokenCliente tipo) {
		ClienteToken registro = vigente(token, tipo);
		if (repository.marcarUsado(registro.getId(), Instant.now()) == 0) {
			throw linkInvalido();
		}
		return registro.getClienteId();
	}

	int borrarVencidosAntesDe(Instant limite) {
		return repository.borrarVencidosAntesDe(limite);
	}

	private ClienteToken vigente(String token, TipoTokenCliente tipo) {
		if (token == null || token.isBlank() || token.length() > LARGO_MAXIMO) {
			throw linkInvalido();
		}
		return repository.findByHash(hash(token))
				.filter(registro -> registro.getTipo() == tipo && registro.vigente(Instant.now()))
				.orElseThrow(TokensCliente::linkInvalido);
	}

	private static NoEncontradoException linkInvalido() {
		return new NoEncontradoException("El link no es válido o ya venció. Pedí uno nuevo.");
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e); // todo JRE lo trae
		}
	}
}
