package com.tiendas.archivos;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Tamaños que se generan al subir una imagen. Generarlos una sola vez al subir (y no en cada request)
 * hace que servir una imagen sea solo leer bytes, sin procesamiento.
 */
public enum TamanioImagen {
	/** Carrito, listados compactos. */
	MINIATURA(200),
	/** Tarjetas de productos en los listados. */
	TARJETA(480),
	/** Detalle de producto. */
	MEDIANA(1080),
	/** Zoom / vista ampliada. */
	GRANDE(2000);

	final int ladoMaximo;

	TamanioImagen(int ladoMaximo) {
		this.ladoMaximo = ladoMaximo;
	}

	String clave(UUID imagenId) {
		return "imagenes/" + imagenId + "/" + nombre();
	}

	public String nombre() {
		return name().toLowerCase(Locale.ROOT);
	}

	static Optional<TamanioImagen> desde(String nombre) {
		for (TamanioImagen tamanio : values()) {
			if (tamanio.nombre().equals(nombre)) {
				return Optional.of(tamanio);
			}
		}
		return Optional.empty();
	}
}
