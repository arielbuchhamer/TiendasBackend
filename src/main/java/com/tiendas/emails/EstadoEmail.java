package com.tiendas.emails;

public enum EstadoEmail {
	/** En cola: se envía (o reintenta) cuando vence {@code proximoIntento}. */
	PENDIENTE,
	ENVIADO,
	/** Se agotaron los reintentos. Queda registrado con el último error. */
	FALLIDO
}
