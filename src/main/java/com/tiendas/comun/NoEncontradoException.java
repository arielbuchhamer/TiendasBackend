package com.tiendas.comun;

/** El recurso pedido no existe (HTTP 404). */
public class NoEncontradoException extends RuntimeException {

	public NoEncontradoException(String mensaje) {
		super(mensaje);
	}

	public static NoEncontradoException de(String recurso, Object id) {
		return new NoEncontradoException(recurso + " " + id + " no encontrado");
	}
}
