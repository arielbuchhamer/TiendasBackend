package com.tiendas.comun;

/**
 * El request es válido en formato pero viola una regla del negocio (HTTP 422).
 * El mensaje se devuelve al cliente, así que debe ser claro y no contener datos internos.
 */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}
}
