package com.tiendas.clientes;

import java.time.Duration;

/** Para qué sirve un link que recibe el cliente por email. */
enum TipoTokenCliente {
	/** Primera contraseña de una cuenta pendiente. */
	ACTIVACION,
	/** Contraseña nueva de una cuenta activa ("olvidé mi contraseña"). */
	RESETEO;

	Duration vigencia(ClientesProperties propiedades) {
		return switch (this) {
			case ACTIVACION -> propiedades.vigenciaInvitacion();
			case RESETEO -> propiedades.vigenciaReseteo();
		};
	}
}
