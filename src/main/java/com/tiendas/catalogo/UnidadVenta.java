package com.tiendas.catalogo;

/**
 * Unidad en la que se vende un producto. Define si stock y cantidades pueden tener decimales
 * (ej. 2,5 m² de deck) o deben ser enteras (ej. 3 celulares).
 */
public enum UnidadVenta {
	UNIDAD(false, "u."),
	METRO(true, "m"),
	METRO_CUADRADO(true, "m²"),
	METRO_CUBICO(true, "m³"),
	PIE(true, "pie"),
	KILOGRAMO(true, "kg"),
	LITRO(true, "l");

	private final boolean admiteDecimales;
	private final String simbolo;

	UnidadVenta(boolean admiteDecimales, String simbolo) {
		this.admiteDecimales = admiteDecimales;
		this.simbolo = simbolo;
	}

	public boolean admiteDecimales() {
		return admiteDecimales;
	}

	public String simbolo() {
		return simbolo;
	}
}
