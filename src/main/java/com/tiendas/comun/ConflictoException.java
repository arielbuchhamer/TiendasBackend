package com.tiendas.comun;

/** El recurso cambió o está en uso y la operación no puede aplicarse (HTTP 409). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}
}
