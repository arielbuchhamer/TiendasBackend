package com.tiendas.archivos;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tiendas.comun.ReglaNegocioException;

import net.coobird.thumbnailator.Thumbnails;

@Service
public class ImagenService {

	/** Límite de píxeles de la imagen original: evita que una imagen gigante ("bomba") agote la memoria. */
	private static final long MAX_PIXELES = 50_000_000L;
	private static final double CALIDAD_JPEG = 0.85;
	private static final long MAX_BYTES_CACHE = 64L * 1024 * 1024;

	private final Almacenamiento almacenamiento;
	private final ImagenRepository imagenRepository;

	// Las imágenes nunca cambian (un reemplazo genera un id nuevo), así que se pueden cachear sin invalidar
	private final Cache<String, Almacenamiento.Archivo> cache = Caffeine.newBuilder()
			.maximumWeight(MAX_BYTES_CACHE)
			.weigher((String clave, Almacenamiento.Archivo archivo) -> archivo.contenido().length)
			.build();

	public ImagenService(Almacenamiento almacenamiento, ImagenRepository imagenRepository) {
		this.almacenamiento = almacenamiento;
		this.imagenRepository = imagenRepository;
	}

	/**
	 * Valida la imagen, genera todos los tamaños y los guarda. Se reencodea siempre: eso también
	 * elimina metadatos EXIF (ubicación GPS, datos de la cámara) de las fotos que sube el admin.
	 */
	public Imagen subir(MultipartFile archivo) {
		BufferedImage original = leer(archivo);
		boolean conTransparencia = original.getColorModel().hasAlpha();
		String formato = conTransparencia ? "png" : "jpg";
		String contentType = conTransparencia ? "image/png" : "image/jpeg";

		UUID id = UUID.randomUUID();
		for (TamanioImagen tamanio : TamanioImagen.values()) {
			almacenamiento.guardar(tamanio.clave(id), redimensionar(original, tamanio, formato), contentType);
		}

		Imagen imagen = new Imagen();
		imagen.setId(id);
		imagen.setContentType(contentType);
		imagen.setAncho(original.getWidth());
		imagen.setAlto(original.getHeight());
		return imagenRepository.save(imagen);
	}

	public Optional<Almacenamiento.Archivo> obtener(UUID id, TamanioImagen tamanio) {
		String clave = tamanio.clave(id);
		Almacenamiento.Archivo enCache = cache.getIfPresent(clave);
		if (enCache != null) {
			return Optional.of(enCache);
		}
		Optional<Almacenamiento.Archivo> archivo = almacenamiento.leer(clave);
		archivo.ifPresent(a -> cache.put(clave, a));
		return archivo;
	}

	private BufferedImage leer(MultipartFile archivo) {
		try (InputStream entrada = archivo.getInputStream();
				ImageInputStream imagenEntrada = ImageIO.createImageInputStream(entrada)) {
			Iterator<ImageReader> lectores = imagenEntrada == null ? null : ImageIO.getImageReaders(imagenEntrada);
			if (lectores == null || !lectores.hasNext()) {
				throw new ReglaNegocioException("Formato de imagen no soportado. Usá JPG, PNG, WebP o GIF.");
			}
			ImageReader lector = lectores.next();
			try {
				lector.setInput(imagenEntrada, true, true);
				// Se validan las dimensiones leyendo solo el encabezado, antes de decodificar los píxeles
				long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
				if (pixeles > MAX_PIXELES) {
					throw new ReglaNegocioException("La imagen es demasiado grande (máximo 50 megapíxeles).");
				}
				return lector.read(0);
			} finally {
				lector.dispose();
			}
		} catch (IOException e) {
			throw new ReglaNegocioException("No se pudo leer la imagen " + archivo.getOriginalFilename());
		}
	}

	private static byte[] redimensionar(BufferedImage original, TamanioImagen tamanio, String formato) {
		try {
			int ladoMayor = Math.max(original.getWidth(), original.getHeight());
			double escala = Math.min(1.0, (double) tamanio.ladoMaximo / ladoMayor); // nunca agranda
			BufferedImage fuente = formato.equals("jpg") ? aRgb(original) : original;

			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			Thumbnails.of(fuente)
					.scale(escala)
					.outputFormat(formato)
					.outputQuality(CALIDAD_JPEG)
					.toOutputStream(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo redimensionar la imagen", e);
		}
	}

	// JPEG no soporta transparencia ni paletas: se normaliza a RGB para evitar colores corruptos
	private static BufferedImage aRgb(BufferedImage imagen) {
		if (imagen.getType() == BufferedImage.TYPE_INT_RGB) {
			return imagen;
		}
		BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		g.drawImage(imagen, 0, 0, null);
		g.dispose();
		return rgb;
	}
}
