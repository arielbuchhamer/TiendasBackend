package com.tiendas.emails;

public enum TipoEmail {
	/** Confirmación de compra al cliente. */
	PEDIDO_APROBADO_CLIENTE,
	/** Aviso de venta al administrador. */
	PEDIDO_APROBADO_ADMIN,
	/** Link para que un cliente nuevo elija su contraseña. */
	CLIENTE_INVITACION,
	/** Link para que un cliente elija una contraseña nueva. */
	CLIENTE_RESETEO_CLAVE
}
