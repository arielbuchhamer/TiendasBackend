package com.tiendas.clientes;

import org.springframework.stereotype.Component;

import com.tiendas.emails.EmailProperties;

/**
 * Las cuentas se activan con un link que llega por email: sin el módulo de emails no habría forma de que un cliente
 * elija su contraseña. Se valida al arrancar para que el error aparezca en el deploy y no con el primer cliente.
 */
@Component
@ClientesHabilitado
class RequisitosClientes {

	RequisitosClientes(EmailProperties email) {
		if (!email.habilitada()) {
			throw new IllegalStateException(
					"Clientes habilitado: requiere el módulo de emails (EMAIL_HABILITADA=true) para enviar las invitaciones");
		}
	}
}
