package com.tiendas.comun;

import java.util.Locale;

/** Normalización de textos que llegan de formularios. */
public final class Textos {

	private Textos() {
	}

	/** trim + minúsculas; {@code null} sigue siendo {@code null}. */
	public static String minusculas(String texto) {
		return texto == null ? null : texto.trim().toLowerCase(Locale.ROOT);
	}

	/** Texto opcional de un formulario: sin espacios alrededor y {@code null} si quedó vacío. */
	public static String opcional(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.trim();
		return limpio.isEmpty() ? null : limpio;
	}
}
