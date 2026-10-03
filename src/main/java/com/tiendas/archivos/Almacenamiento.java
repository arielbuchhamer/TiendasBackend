package com.tiendas.archivos;

import java.util.Optional;

/**
 * Almacenamiento de archivos binarios (imágenes, PDFs). Hay dos implementaciones, elegidas por
 * configuración ({@code almacenamiento.tipo}): disco local para desarrollo y S3-compatible
 * (Cloudflare R2, AWS S3, MinIO) para producción.
 * <p>
 * Las claves las genera siempre el servidor (nunca vienen del cliente) y no se exponen
 * archivos directamente: se sirven a través de endpoints que controlan el acceso.
 */
public interface Almacenamiento {

	void guardar(String clave, byte[] contenido, String contentType);

	Optional<Archivo> leer(String clave);

	void eliminar(String clave);

	record Archivo(byte[] contenido, String contentType) {
	}
}
