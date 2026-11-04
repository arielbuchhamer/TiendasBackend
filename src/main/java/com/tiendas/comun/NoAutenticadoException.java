package com.tiendas.comun;

/** Hace falta iniciar sesión, o la sesión ya no sirve (ej. la cuenta se bloqueó) (HTTP 401). */
public class NoAutenticadoException extends RuntimeException {

	public NoAutenticadoException(String mensaje) {
		super(mensaje);
	}
}
