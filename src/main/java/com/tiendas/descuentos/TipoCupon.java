package com.tiendas.descuentos;

public enum TipoCupon {

	/** Descuenta un porcentaje ({@code valor}, de 1 a 100) de los productos alcanzados, con un {@code tope} opcional. */
	PORCENTAJE,

	/** Descuenta un monto fijo ({@code valor}), nunca más que el total de los productos alcanzados. */
	MONTO_FIJO,

	/**
	 * Envío sin cargo. Solo aplica a pedidos con envío a domicilio. Descuenta el costo de envío que cobra la
	 * web; si la web no lo cobra (se coordina aparte), el pedido queda marcado con el cupón y descuento cero.
	 */
	ENVIO_GRATIS
}
