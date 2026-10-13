package com.tiendas.correo;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Códigos de provincia de una letra que usa Correo Argentino (manual de la API, "Province Codes"). */
public final class Provincias {

	private static final Map<String, String> NOMBRES = new LinkedHashMap<>();
	private static final Map<String, String> CODIGOS = new LinkedHashMap<>();

	static {
		agregar("A", "Salta");
		agregar("B", "Buenos Aires", "Provincia de Buenos Aires", "Bs As", "PBA");
		agregar("C", "Ciudad Autónoma de Buenos Aires", "CABA", "Capital Federal", "Ciudad de Buenos Aires");
		agregar("D", "San Luis");
		agregar("E", "Entre Ríos");
		agregar("F", "La Rioja");
		agregar("G", "Santiago del Estero");
		agregar("H", "Chaco");
		agregar("J", "San Juan");
		agregar("K", "Catamarca");
		agregar("L", "La Pampa");
		agregar("M", "Mendoza");
		agregar("N", "Misiones");
		agregar("P", "Formosa");
		agregar("Q", "Neuquén");
		agregar("R", "Río Negro");
		agregar("S", "Santa Fe");
		agregar("T", "Tucumán");
		agregar("U", "Chubut");
		agregar("V", "Tierra del Fuego", "Tierra del Fuego, Antártida e Islas del Atlántico Sur");
		agregar("W", "Corrientes");
		agregar("X", "Córdoba");
		agregar("Y", "Jujuy");
		agregar("Z", "Santa Cruz");
	}

	private Provincias() {
	}

	private static void agregar(String codigo, String nombre, String... otrosNombres) {
		NOMBRES.put(codigo, nombre);
		CODIGOS.put(clave(nombre), codigo);
		for (String otro : otrosNombres) {
			CODIGOS.put(clave(otro), codigo);
		}
	}

	/** Código del Correo para el nombre que escribió el cliente (sin importar tildes ni mayúsculas), o null. */
	public static String codigo(String nombre) {
		if (nombre == null) {
			return null;
		}
		String limpio = nombre.trim();
		if (limpio.length() == 1 && NOMBRES.containsKey(limpio.toUpperCase(Locale.ROOT))) {
			return limpio.toUpperCase(Locale.ROOT);
		}
		return CODIGOS.get(clave(limpio));
	}

	public static String nombre(String codigo) {
		return NOMBRES.getOrDefault(codigo, codigo);
	}

	private static String clave(String texto) {
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}
}
