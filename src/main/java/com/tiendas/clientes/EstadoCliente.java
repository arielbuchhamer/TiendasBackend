package com.tiendas.clientes;

public enum EstadoCliente {
	/** Dado de alta por el administrador; todavía no eligió su contraseña. */
	PENDIENTE,
	/** Puede iniciar sesión y comprar. */
	ACTIVO,
	/** No puede iniciar sesión ni comprar (lo decide el administrador). */
	BLOQUEADO
}
