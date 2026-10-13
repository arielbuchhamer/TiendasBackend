package com.tiendas.pedidos;

public enum TipoEntrega {
	RETIRO,
	DOMICILIO,
	/** Retira en una sucursal de Correo Argentino ({@code Pedido.envioSucursal}). */
	SUCURSAL
}
