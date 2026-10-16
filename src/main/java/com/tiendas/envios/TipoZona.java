package com.tiendas.envios;

public enum TipoZona {

	/** El dueño fija el precio (o lo deja a convenir). */
	PRECIO_FIJO,

	/** Precio y plazo los cotiza Correo Argentino según el código postal, el peso y las medidas. */
	CORREO_ARGENTINO,

	/**
	 * Precio y plazo los cotiza Zipnova (con Andreani, Correo Argentino, OCA y otros transportes) según el destino, el
	 * peso y las medidas.
	 */
	ZIPNOVA
}
