package com.tiendas.facturacion;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Genera el PDF de un comprobante autorizado (A4, con logo y color de marca configurables). */
@Component
@FacturacionHabilitada
class ComprobantePdfService {

	private static final Logger log = LoggerFactory.getLogger(ComprobantePdfService.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final float MARGIN = 42;
	private static final float CONTENT_WIDTH = 511;
	private static final float LINE_HEIGHT = 16;
	private static final float TABLE_ROW_HEIGHT = 22;
	private static final float TOP_BAR_HEIGHT = 6;
	private static final float LOGO_HEIGHT = 30;

	private static final Color TEXT_DARK = new Color(35, 35, 40);
	private static final Color TEXT_MUTED = new Color(120, 120, 128);
	private static final Color LINE_LIGHT = new Color(228, 228, 232);
	private static final Color ROW_TINT = new Color(248, 248, 250);

	private static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
	private static final PDFont NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
	private final byte[] logoBytes;
	private final Color colorAcento;
	private final Color colorAcentoSuave;

	ComprobantePdfService(FacturacionProperties facturacion, ResourceLoader resourceLoader) {
		this.logoBytes = cargarLogo(facturacion.pdf().logo(), resourceLoader);
		this.colorAcento = parsearColor(facturacion.pdf().colorAcento());
		this.colorAcentoSuave = suavizar(this.colorAcento, 0.92f);
	}

	byte[] generar(Comprobante comprobante) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			PDImageXObject logo = logoBytes == null ? null : PDImageXObject.createFromByteArray(document, logoBytes, "logo");

			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				float pageWidth = page.getMediaBox().getWidth();
				float y = page.getMediaBox().getHeight();

				fill(content, 0, y - TOP_BAR_HEIGHT, pageWidth, TOP_BAR_HEIGHT, colorAcento);
				y -= TOP_BAR_HEIGHT + MARGIN;

				y = dibujarEncabezado(content, comprobante, logo, y);
				y = dibujarCliente(content, comprobante.getSolicitud(), y - 16);
				y = dibujarItems(content, comprobante.getSolicitud(), y - 16);
				y = dibujarTotales(content, comprobante.getSolicitud().getImportes(), y - 12);
				dibujarDatosFiscales(content, comprobante, y - 18);
			}

			document.save(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el PDF del comprobante " + comprobante.getCodigo(), e);
		}
	}

	private float dibujarEncabezado(PDPageContentStream content, Comprobante comprobante, PDImageXObject logo, float y) throws IOException {
		ComprobanteSolicitud solicitud = comprobante.getSolicitud();
		ComprobanteSolicitud.DatosEmisor emisor = solicitud.getEmisor();
		float logoBottom = y;

		if (logo != null) {
			float scale = LOGO_HEIGHT / logo.getHeight();
			float logoWidth = logo.getWidth() * scale;
			content.drawImage(logo, MARGIN, y - LOGO_HEIGHT, logoWidth, LOGO_HEIGHT);
			logoBottom = y - LOGO_HEIGHT - 14;
		} else {
			text(content, valor(emisor.getRazonSocial()), MARGIN, y - 16, NEGRITA, 18, TEXT_DARK);
			logoBottom = y - 34;
		}

		text(content, valor(emisor.getRazonSocial()), MARGIN, logoBottom, NORMAL, 9, TEXT_MUTED);
		text(content, datosEmisorLinea(emisor), MARGIN, logoBottom - 14, NORMAL, 9, TEXT_MUTED);

		String letra = comprobante.getTipo().letra();
		float badgeSize = 34;
		float badgeX = 300;
		fill(content, badgeX, y - badgeSize + 6, badgeSize, badgeSize, colorAcento);
		textCentrada(content, letra, badgeX, badgeX + badgeSize, y - badgeSize + 16, NEGRITA, 20, Color.WHITE);
		text(content, "COD. " + comprobante.getTipo().getCodigoArca(), badgeX, y - badgeSize - 6, NORMAL, 8, TEXT_MUTED);

		float infoX = 366;
		text(content, comprobante.getTipo().name().replace("_", " "), infoX, y - 12, NEGRITA, 14, TEXT_DARK);
		text(content, "Nro: " + comprobante.numeroCompleto(), infoX, y - 30, NORMAL, 10, TEXT_MUTED);
		text(content, "Fecha: " + fecha(solicitud.getFecha()), infoX, y - 44, NORMAL, 10, TEXT_MUTED);
		text(content, "IVA: " + valor(emisor.getCondicionIva()), infoX, y - 58, NORMAL, 10, TEXT_MUTED);

		float bottom = Math.min(logoBottom - 22, y - badgeSize - 20);
		linea(content, MARGIN, bottom, MARGIN + CONTENT_WIDTH, bottom, LINE_LIGHT, 1);
		return bottom;
	}

	private float dibujarCliente(PDPageContentStream content, ComprobanteSolicitud solicitud, float y) throws IOException {
		ComprobanteSolicitud.DatosReceptor cliente = solicitud.getCliente();
		text(content, "CLIENTE", MARGIN, y, NEGRITA, 9, colorAcento);

		float rowY = y - 20;
		text(content, nombreCliente(cliente), MARGIN, rowY, NORMAL, 10, TEXT_DARK);
		rowY -= 16;
		text(content, valor(cliente.getTipoDocumento()) + " " + valor(cliente.getNumeroDocumento()), MARGIN, rowY, NORMAL, 10, TEXT_MUTED);
		text(content, "Cond. IVA: " + valor(cliente.getCondicionIva()), 310, rowY, NORMAL, 10, TEXT_MUTED);
		rowY -= 16;
		text(content, valor(cliente.getDomicilio()), MARGIN, rowY, NORMAL, 10, TEXT_MUTED);

		float bottom = rowY - 14;
		linea(content, MARGIN, bottom, MARGIN + CONTENT_WIDTH, bottom, LINE_LIGHT, 1);
		return bottom;
	}

	private float dibujarItems(PDPageContentStream content, ComprobanteSolicitud solicitud, float y) throws IOException {
		float tableX = MARGIN;
		float descX = tableX + 8;
		float qtyX = tableX + 285;
		float priceX = tableX + 345;
		float ivaX = tableX + 410;
		float totalX = tableX + 453;

		fill(content, tableX, y - TABLE_ROW_HEIGHT, CONTENT_WIDTH, TABLE_ROW_HEIGHT, colorAcentoSuave);
		text(content, "DESCRIPCION", descX, y - 15, NEGRITA, 8, colorAcento);
		text(content, "CANT.", qtyX, y - 15, NEGRITA, 8, colorAcento);
		text(content, "UNIT.", priceX, y - 15, NEGRITA, 8, colorAcento);
		text(content, "IVA", ivaX, y - 15, NEGRITA, 8, colorAcento);
		text(content, "SUBTOTAL", totalX, y - 15, NEGRITA, 8, colorAcento);

		y -= TABLE_ROW_HEIGHT;
		boolean alterna = false;
		for (ComprobanteSolicitud.Item item : solicitud.getItems()) {
			if (alterna) {
				fill(content, tableX, y - TABLE_ROW_HEIGHT, CONTENT_WIDTH, TABLE_ROW_HEIGHT, ROW_TINT);
			}
			text(content, cortar(valor(item.getDescripcion()), 48), descX, y - 15, NORMAL, 9, TEXT_DARK);
			text(content, numero(item.getCantidad()), qtyX, y - 15, NORMAL, 9, TEXT_DARK);
			text(content, moneda(item.getPrecioUnitario()), priceX, y - 15, NORMAL, 9, TEXT_DARK);
			text(content, porcentajeIva(item), ivaX, y - 15, NORMAL, 9, TEXT_DARK);
			text(content, moneda(item.getSubtotal()), totalX, y - 15, NORMAL, 9, TEXT_DARK);
			linea(content, tableX, y - TABLE_ROW_HEIGHT, tableX + CONTENT_WIDTH, y - TABLE_ROW_HEIGHT, LINE_LIGHT, 0.5f);
			y -= TABLE_ROW_HEIGHT;
			alterna = !alterna;
		}

		return y;
	}

	private float dibujarTotales(PDPageContentStream content, ComprobanteSolicitud.Importes importes, float y) throws IOException {
		float xLabel = 380;
		float xValue = 470;
		text(content, "Neto:", xLabel, y, NORMAL, 10, TEXT_MUTED);
		text(content, moneda(importes.getNeto()), xValue, y, NORMAL, 10, TEXT_DARK);
		text(content, "IVA:", xLabel, y - LINE_HEIGHT, NORMAL, 10, TEXT_MUTED);
		text(content, moneda(importes.getIva()), xValue, y - LINE_HEIGHT, NORMAL, 10, TEXT_DARK);
		text(content, "Tributos:", xLabel, y - LINE_HEIGHT * 2, NORMAL, 10, TEXT_MUTED);
		text(content, moneda(importes.getTributos()), xValue, y - LINE_HEIGHT * 2, NORMAL, 10, TEXT_DARK);

		float lineY = y - LINE_HEIGHT * 2 - 10;
		linea(content, xLabel, lineY, xLabel + 131, lineY, colorAcento, 1.4f);
		float totalY = lineY - 20;
		text(content, "Total:", xLabel, totalY, NEGRITA, 13, TEXT_DARK);
		text(content, moneda(importes.getTotal()), xValue, totalY, NEGRITA, 13, colorAcento);
		return totalY - 16;
	}

	private void dibujarDatosFiscales(PDPageContentStream content, Comprobante comprobante, float y) throws IOException {
		float height = 54;
		fill(content, MARGIN, y - height, CONTENT_WIDTH, height, colorAcentoSuave);
		fill(content, MARGIN, y - height, 4, height, colorAcento);
		text(content, "Comprobante autorizado por ARCA", MARGIN + 16, y - 20, NEGRITA, 10, colorAcento);
		text(content, "CAE: " + valor(comprobante.getCae()), MARGIN + 16, y - 38, NORMAL, 10, TEXT_DARK);
		text(content, "Vto. CAE: " + fecha(comprobante.getCaeVencimiento()), 310, y - 38, NORMAL, 10, TEXT_DARK);
	}

	private void text(PDPageContentStream content, String text, float x, float y, PDFont font, int size, Color color) throws IOException {
		content.beginText();
		content.setNonStrokingColor(color);
		content.setFont(font, size);
		content.newLineAtOffset(x, y);
		content.showText(sanitizar(text));
		content.endText();
		content.setNonStrokingColor(Color.BLACK);
	}

	private void textCentrada(PDPageContentStream content, String text, float xDesde, float xHasta, float y, PDFont font, int size, Color color) throws IOException {
		String sanitizado = sanitizar(text);
		float textWidth = font.getStringWidth(sanitizado) / 1000 * size;
		float x = xDesde + ((xHasta - xDesde) - textWidth) / 2;
		text(content, text, x, y, font, size, color);
	}

	private void linea(PDPageContentStream content, float x1, float y1, float x2, float y2, Color color, float width) throws IOException {
		content.setStrokingColor(color);
		content.setLineWidth(width);
		content.moveTo(x1, y1);
		content.lineTo(x2, y2);
		content.stroke();
	}

	private void fill(PDPageContentStream content, float x, float y, float width, float height, Color color) throws IOException {
		content.setNonStrokingColor(color);
		content.addRect(x, y, width, height);
		content.fill();
		content.setNonStrokingColor(Color.BLACK);
	}

	// El logo es opcional: si no se configuró o no se puede leer, el PDF muestra la razón social
	private static byte[] cargarLogo(String ubicacion, ResourceLoader resourceLoader) {
		if (!StringUtils.hasText(ubicacion)) {
			return null;
		}
		Resource recurso = resourceLoader.getResource(ubicacion);
		try (var entrada = recurso.getInputStream()) {
			return entrada.readAllBytes();
		} catch (IOException e) {
			log.warn("No se pudo leer el logo de facturación '{}': {}", ubicacion, e.getMessage());
			return null;
		}
	}

	private static Color parsearColor(String hex) {
		try {
			return Color.decode(hex);
		} catch (NumberFormatException e) {
			return new Color(79, 70, 229);
		}
	}

	private static Color suavizar(Color base, float haciaBlanco) {
		int r = (int) (base.getRed() + (255 - base.getRed()) * haciaBlanco);
		int g = (int) (base.getGreen() + (255 - base.getGreen()) * haciaBlanco);
		int b = (int) (base.getBlue() + (255 - base.getBlue()) * haciaBlanco);
		return new Color(r, g, b);
	}

	private String nombreCliente(ComprobanteSolicitud.DatosReceptor cliente) {
		if (cliente == null) {
			return "";
		}
		if (cliente.getRazonSocial() != null && !cliente.getRazonSocial().isBlank()) {
			return cliente.getRazonSocial();
		}
		return (valor(cliente.getNombre()) + " " + valor(cliente.getApellido())).trim();
	}

	private String porcentajeIva(ComprobanteSolicitud.Item item) {
		if (item.getAlicuotaIva() == null) {
			return "";
		}
		return numero(item.getAlicuotaIva().getPorcentaje()) + "%";
	}

	private String moneda(BigDecimal value) {
		return "$ " + moneyFormat.format(orZero(value));
	}

	private String numero(BigDecimal value) {
		return orZero(value).stripTrailingZeros().toPlainString();
	}

	private BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}

	private String fecha(java.time.LocalDate fecha) {
		return fecha == null ? "" : DATE_FORMATTER.format(fecha);
	}

	private String valor(Object value) {
		return value == null ? "" : value.toString().replace("_", " ");
	}

	private String datosEmisorLinea(ComprobanteSolicitud.DatosEmisor emisor) {
		String linea = "CUIT " + valor(emisor.getCuit());
		if (emisor.getDomicilio() != null && !emisor.getDomicilio().isBlank()) {
			linea += "  -  " + emisor.getDomicilio();
		}
		return linea;
	}

	private String cortar(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	private String sanitizar(String value) {
		return value == null ? "" : value.replaceAll("[^\\p{Print}\\p{IsLatin}]", "?");
	}
}
