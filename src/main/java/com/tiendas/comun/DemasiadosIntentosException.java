package com.tiendas.comun;

/** Se superó un límite de intentos (fuerza bruta, abuso de un formulario público) (HTTP 429). */
public class DemasiadosIntentosException extends RuntimeException {

	public DemasiadosIntentosException(String mensaje) {
		super(mensaje);
	}
}
