package com.tiendas.archivos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/** Guarda los archivos en disco. Pensado para desarrollo local (en Railway el disco es efímero). */
class AlmacenamientoLocal implements Almacenamiento {

	private static final Pattern CLAVE_VALIDA = Pattern.compile("[a-z0-9][a-z0-9/._-]*");

	private final Path raiz;

	AlmacenamientoLocal(Path raiz) {
		this.raiz = raiz.toAbsolutePath().normalize();
	}

	@Override
	public void guardar(String clave, byte[] contenido, String contentType) {
		Path destino = resolver(clave);
		try {
			Files.createDirectories(destino.getParent());
			Files.write(destino, contenido);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar el archivo " + clave, e);
		}
	}

	@Override
	public Optional<Archivo> leer(String clave) {
		try {
			byte[] contenido = Files.readAllBytes(resolver(clave));
			return Optional.of(new Archivo(contenido, detectarContentType(contenido)));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer el archivo " + clave, e);
		}
	}

	@Override
	public void eliminar(String clave) {
		try {
			Files.deleteIfExists(resolver(clave));
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo eliminar el archivo " + clave, e);
		}
	}

	// Defensa en profundidad contra path traversal, aunque las claves siempre las genera el servidor
	private Path resolver(String clave) {
		if (!CLAVE_VALIDA.matcher(clave).matches() || clave.contains("..")) {
			throw new IllegalArgumentException("Clave de archivo inválida: " + clave);
		}
		Path ruta = raiz.resolve(clave).normalize();
		if (!ruta.startsWith(raiz)) {
			throw new IllegalArgumentException("Clave de archivo inválida: " + clave);
		}
		return ruta;
	}

	// En disco no hay metadatos: se reconoce el tipo por la firma de los primeros bytes
	private static String detectarContentType(byte[] contenido) {
		if (empiezaCon(contenido, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (empiezaCon(contenido, 0x89, 'P', 'N', 'G')) {
			return "image/png";
		}
		if (empiezaCon(contenido, '%', 'P', 'D', 'F')) {
			return "application/pdf";
		}
		return "application/octet-stream";
	}

	private static boolean empiezaCon(byte[] contenido, int... firma) {
		if (contenido.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if ((contenido[i] & 0xFF) != firma[i]) {
				return false;
			}
		}
		return true;
	}
}
