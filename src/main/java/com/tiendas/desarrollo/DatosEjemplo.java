package com.tiendas.desarrollo;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tiendas.archivos.ImagenService;
import com.tiendas.catalogo.Categoria;
import com.tiendas.catalogo.CategoriaService;
import com.tiendas.catalogo.Producto;
import com.tiendas.catalogo.ProductoService;
import com.tiendas.catalogo.Rubro;
import com.tiendas.catalogo.RubroService;
import com.tiendas.catalogo.UnidadVenta;
import com.tiendas.catalogo.Variante;

/**
 * Carga un catálogo de ejemplo (rubros de HA!Tablas) para desarrollar el frontend sin arrancar con la base
 * vacía. Solo en el perfil {@code local} y solo si no hay rubros cargados: nunca pisa datos existentes.
 * Las imágenes son placeholders generados (color + nombre del producto).
 */
@Component
@Profile("local")
class DatosEjemplo implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DatosEjemplo.class);

	private final RubroService rubroService;
	private final CategoriaService categoriaService;
	private final ProductoService productoService;
	private final ImagenService imagenService;

	DatosEjemplo(RubroService rubroService, CategoriaService categoriaService, ProductoService productoService,
			ImagenService imagenService) {
		this.rubroService = rubroService;
		this.categoriaService = categoriaService;
		this.productoService = productoService;
		this.imagenService = imagenService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!rubroService.listar().isEmpty()) {
			return;
		}
		Color madera = new Color(156, 102, 56);
		Color calabaza = new Color(122, 132, 70);
		Color acero = new Color(90, 100, 110);
		Color cuero = new Color(110, 66, 40);

		Rubro tablas = rubro("Tablas", 1);
		Categoria picada = categoria(tablas, "Tablas de picada", 1);
		Categoria asado = categoria(tablas, "Tablas de asado", 2);
		Categoria servidoras = categoria(tablas, "Servidoras", 3);

		Rubro mates = rubro("Mates y materas", 2);
		Categoria categoriaMates = categoria(mates, "Mates", 1);
		Categoria materas = categoria(mates, "Materas", 2);

		Rubro termos = rubro("Termos y vasos", 3);
		Categoria categoriaTermos = categoria(termos, "Termos", 1);
		Categoria vasos = categoria(termos, "Vasos", 2);

		Rubro asador = rubro("Para el asado", 4);
		Categoria cuchillos = categoria(asador, "Cuchillos", 1);

		Rubro regalos = rubro("Regalería", 5);
		Categoria llaveros = categoria(regalos, "Llaveros", 1);
		Categoria cuencos = categoria(regalos, "Cuencos", 2);

		producto(picada, "Tabla de picada de algarrobo", "Algarrobo macizo con grabado láser personalizado.",
				"18500", UnidadVenta.UNIDAD, true, madera,
				variante(Map.of("medida", "30 x 20 cm"), null, "25"),
				variante(Map.of("medida", "40 x 25 cm"), "24500", "12"));
		producto(picada, "Tabla redonda con manija", "Ideal para fiambres y quesos. Grabado a elección.",
				"21000", UnidadVenta.UNIDAD, false, madera,
				variante(Map.of(), null, "8"));
		producto(asado, "Tabla de asado con canaleta", "Canaleta perimetral para jugos. Grabamos tu escudo o logo.",
				"32000", UnidadVenta.UNIDAD, true, madera,
				variante(Map.of("medida", "45 x 30 cm"), null, "10"),
				variante(Map.of("medida", "60 x 40 cm"), "41000", "4"));
		producto(servidoras, "Servidora de pino", "Servidora rectangular con borde. Precio por unidad.",
				"14000", UnidadVenta.UNIDAD, false, madera,
				variante(Map.of(), null, "30"));
		producto(categoriaMates, "Mate de calabaza con virola", "Calabaza curada con virola de alpaca.",
				"16500", UnidadVenta.UNIDAD, true, calabaza,
				variante(Map.of("virola", "Lisa"), null, "15"),
				variante(Map.of("virola", "Cincelada"), "19800", "6"));
		producto(materas, "Matera de cuero", "Matera de cuero vacuno con grabado.",
				"38000", UnidadVenta.UNIDAD, false, cuero,
				variante(Map.of("color", "Suela"), null, "5"),
				variante(Map.of("color", "Negro"), null, "0"));
		producto(categoriaTermos, "Termo de acero 1 L grabado", "Termo de acero inoxidable con grabado láser.",
				"45000", UnidadVenta.UNIDAD, true, acero,
				variante(Map.of("color", "Verde"), null, "7"),
				variante(Map.of("color", "Negro"), null, "9"));
		producto(vasos, "Vaso térmico 500 ml", "Doble pared, tapa con sorbete.", "22000", UnidadVenta.UNIDAD, false,
				acero, variante(Map.of(), null, "20"));
		producto(cuchillos, "Cuchillo de asado 8\" con cabo de madera", "Hoja de acero, cabo de algarrobo grabado.",
				"27000", UnidadVenta.UNIDAD, false, acero, variante(Map.of(), null, "12"));
		producto(llaveros, "Llavero de madera grabado", "Grabamos nombre, escudo o logo. Ideal para eventos.",
				"3500", UnidadVenta.UNIDAD, false, madera, variante(Map.of(), null, "200"));
		producto(cuencos, "Cuenco de madera torneado", "Cuenco para snacks, torneado a mano.", "12500",
				UnidadVenta.UNIDAD, false, madera, variante(Map.of("diámetro", "15 cm"), null, "10"),
				variante(Map.of("diámetro", "20 cm"), "15500", "6"));

		log.info("Se cargó el catálogo de ejemplo (perfil local)");
	}

	private Rubro rubro(String nombre, int orden) {
		Rubro rubro = new Rubro();
		rubro.setNombre(nombre);
		rubro.setOrden(orden);
		return rubroService.crear(rubro);
	}

	private Categoria categoria(Rubro rubro, String nombre, int orden) {
		Categoria categoria = new Categoria();
		categoria.setRubroId(rubro.getId());
		categoria.setNombre(nombre);
		categoria.setOrden(orden);
		return categoriaService.crear(categoria);
	}

	private void producto(Categoria categoria, String nombre, String descripcion, String precio, UnidadVenta unidad,
			boolean destacado, Color color, Variante... variantes) {
		Producto producto = new Producto();
		producto.setCategoriaId(categoria.getId());
		producto.setNombre(nombre);
		producto.setDescripcion(descripcion);
		producto.setPrecio(new BigDecimal(precio));
		producto.setUnidadVenta(unidad);
		producto.setDestacado(destacado);
		producto.setImagenes(new ArrayList<>(List.of(
				imagenService.subir(new ByteArrayInputStream(placeholder(nombre, color)), nombre).getId())));
		producto.setVariantes(new ArrayList<>(List.of(variantes)));
		productoService.crear(producto);
	}

	private static Variante variante(Map<String, String> atributos, String precio, String stock) {
		Variante variante = new Variante();
		variante.setAtributos(new LinkedHashMap<>(atributos));
		variante.setPrecio(precio == null ? null : new BigDecimal(precio));
		variante.setStock(new BigDecimal(stock));
		return variante;
	}

	private static byte[] placeholder(String texto, Color color) {
		BufferedImage imagen = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, color.brighter(), 1200, 1200, color.darker()));
		g.fillRect(0, 0, 1200, 1200);
		g.setColor(new Color(255, 255, 255, 230));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
		int y = 560;
		for (String linea : partirEnLineas(texto, 22)) {
			int ancho = g.getFontMetrics().stringWidth(linea);
			g.drawString(linea, (1200 - ancho) / 2, y);
			y += 84;
		}
		g.dispose();
		try {
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			ImageIO.write(imagen, "jpg", salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<String> partirEnLineas(String texto, int maximo) {
		List<String> lineas = new ArrayList<>();
		StringBuilder actual = new StringBuilder();
		for (String palabra : texto.split(" ")) {
			if (actual.length() + palabra.length() + 1 > maximo && !actual.isEmpty()) {
				lineas.add(actual.toString());
				actual.setLength(0);
			}
			actual.append(actual.isEmpty() ? "" : " ").append(palabra);
		}
		lineas.add(actual.toString());
		return lineas;
	}
}
