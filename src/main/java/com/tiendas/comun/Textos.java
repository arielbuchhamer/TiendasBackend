package com.tiendas.comun;

import java.util.Locale;

/** Normalización de textos que se comparan sin importar mayúsculas (usuarios, emails). */
public final class Textos {

	private Textos() {
	}

	/** trim + minúsculas; {@code null} sigue siendo {@code null}. */
	public static String minusculas(String texto) {
		return texto == null ? null : texto.trim().toLowerCase(Locale.ROOT);
	}
}
