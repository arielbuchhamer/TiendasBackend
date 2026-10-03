package com.tiendas.archivos;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.tiendas.comun.ReglaNegocioException;

@RestController
@RequestMapping("/api/v1")
public class ImagenController {

	private static final int MAX_ARCHIVOS_POR_REQUEST = 10;

	private final ImagenService imagenService;

	public ImagenController(ImagenService imagenService) {
		this.imagenService = imagenService;
	}

	/**
	 * Sirve una imagen. La respuesta es inmutable y cacheable por un año: el navegador y un CDN
	 * delante de la API (ej. Cloudflare) la sirven sin volver a pedirla al servidor.
	 * Tamaños: miniatura, tarjeta, mediana, grande.
	 */
	@GetMapping("/imagenes/{id}/{tamanio}")
	public ResponseEntity<byte[]> obtener(@PathVariable UUID id, @PathVariable String tamanio, WebRequest request) {
		TamanioImagen tamanioImagen = TamanioImagen.desde(tamanio).orElse(null);
		if (tamanioImagen == null) {
			return ResponseEntity.notFound().build();
		}
		String etag = "\"" + id + "-" + tamanioImagen.nombre() + "\"";
		if (request.checkNotModified(etag)) {
			return null; // Spring responde 304 Not Modified
		}
		return imagenService.obtener(id, tamanioImagen)
				.map(archivo -> ResponseEntity.ok()
						.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
						.eTag(etag)
						.contentType(MediaType.parseMediaType(archivo.contentType()))
						.body(archivo.contenido()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping(value = "/admin/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public List<Imagen> subir(@RequestParam("archivos") List<MultipartFile> archivos) {
		if (archivos.isEmpty() || archivos.size() > MAX_ARCHIVOS_POR_REQUEST) {
			throw new ReglaNegocioException("Enviá entre 1 y " + MAX_ARCHIVOS_POR_REQUEST + " imágenes por request");
		}
		return archivos.stream().map(imagenService::subir).toList();
	}
}
