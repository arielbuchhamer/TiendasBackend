package com.tiendas.zipnova;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zipnova de mentira para desarrollar sin credenciales: precios y plazos inventados (crecen con el peso y la
 * distancia entre códigos postales), sucursales de ejemplo en la ciudad del cliente, una etiqueta PDF mínima y un
 * seguimiento fijo. Solo se crea en el perfil local (ver {@link ZipnovaConfig}).
 */
class ZipnovaSimulado implements Zipnova {

	private static final Logger log = LoggerFactory.getLogger(ZipnovaSimulado.class);
	/** Código postal inventado desde donde se "despacha" (el mismo origen que usa el Correo simulado en local). */
	private static final int ORIGEN = 3177;

	private final AtomicLong ids = new AtomicLong(900_000);

	ZipnovaSimulado() {
		log.warn("ZIPNOVA SIMULADO: cotizaciones, sucursales y envíos inventados (solo desarrollo local)");
	}

	@Override
	public Cotizacion cotizar(Destino destino, List<Item> items, BigDecimal valorDeclarado) {
		double kilos = items.stream().mapToDouble(i -> i.pesoGramos() / 1000.0).sum();
		int distancia = Math.abs(Integer.parseInt(destino.codigoPostal()) - ORIGEN);
		boolean lejos = distancia > 400;
		double domicilio = 4200 + 1100 * Math.ceil(kilos) + Math.min(distancia, 6000) * 0.8;
		String ciudad = destino.ciudad() == null || destino.ciudad().isBlank() ? "Centro" : destino.ciudad().trim();
		List<Punto> puntos = List.of(
				new Punto("50101", "Sucursal " + ciudad + " Centro", "San Martín 450", ciudad, destino.provincia(),
						destino.codigoPostal(), "Lun a vie 9 a 18"),
				new Punto("50102", "Sucursal " + ciudad + " Norte", "Belgrano 1200", ciudad, destino.provincia(),
						destino.codigoPostal(), "Lun a sáb 8 a 21"));
		String fecha = java.time.LocalDate.now().plusDays(lejos ? 7 : 5).toString();
		return new Cotizacion(ciudad, destino.provincia(), List.of(
				new Opcion("standard_delivery", "Entrega a domicilio", false, 208, "OCA", null, "carrier_dropoff",
						precio(domicilio), 2, lejos ? 6 : 4, fecha, List.of()),
				new Opcion("standard_delivery", "Entrega a domicilio", false, 233, "Correo Argentino", null,
						"carrier_dropoff", precio(domicilio * 1.2), 3, lejos ? 7 : 5, fecha, List.of()),
				new Opcion(RETIRO_EN_PUNTO, "Retiro en sucursal", false, 233, "Correo Argentino", null,
						"carrier_dropoff", precio(domicilio * 0.78), 3, lejos ? 7 : 5, fecha, puntos),
				new Opcion("express_delivery", "Entrega express", true, 208, "OCA", null, "carrier_dropoff",
						precio(domicilio * 1.7), 1, lejos ? 3 : 2, fecha, List.of())));
	}

	private static BigDecimal precio(double valor) {
		return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
	}

	@Override
	public Envio darDeAlta(AltaEnvio envio) {
		String id = String.valueOf(ids.incrementAndGet());
		log.info("Zipnova simulado: alta del envío {} ({}, transporte {}) para {}", envio.idExterno(), envio.servicio(),
				envio.transportistaId(), envio.destinatario());
		return new Envio(id, "Nuevo", envio.transportistaId() == 233 ? "Correo Argentino" : "OCA",
				"SIM" + id, "https://www.zipnova.com.ar/");
	}

	@Override
	public Envio envio(String id) {
		return new Envio(id, "En camino", "OCA", "SIM" + id, "https://www.zipnova.com.ar/");
	}

	@Override
	public byte[] etiqueta(String id) {
		return pdfMinimo("Etiqueta de prueba - envio " + id);
	}

	@Override
	public List<EventoSeguimiento> seguimiento(String id) {
		return List.of(
				new EventoSeguimiento("2026-10-15T11:20:00-03:00", "En camino", ""),
				new EventoSeguimiento("2026-10-14T16:05:00-03:00", "Despachado", ""),
				new EventoSeguimiento("2026-10-14T09:40:00-03:00", "Nuevo", ""));
	}

	@Override
	public java.util.Optional<byte[]> guia(String id) {
		return java.util.Optional.of(pdfMinimo("Guia de despacho de prueba - envio " + id));
	}

	@Override
	public Cancelacion cancelar(String id) {
		log.info("Zipnova simulado: envío {} cancelado", id);
		return new Cancelacion(false);
	}

	@Override
	public List<Punto> puntosDeDespacho(String id) {
		return List.of(
				new Punto("9001", "Correo Argentino - Sucursal Centro", "Roca 289", "Origen", null, null, null),
				new Punto("9002", "Correo Argentino - Kiosco Plaza", "Perón 1035", "Origen", null, null, null));
	}

	@Override
	public Saldo saldo() {
		return new Saldo(new BigDecimal("-12000.00"), new BigDecimal("50000.00"), new BigDecimal("38000.00"));
	}

	/** Un PDF de una página con una línea de texto, para probar la descarga sin Zipnova. */
	private static byte[] pdfMinimo(String texto) {
		String contenido = "BT /F1 18 Tf 40 760 Td (" + texto + ") Tj ET";
		String[] objetos = {
				"<< /Type /Catalog /Pages 2 0 R >>",
				"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
						+ "/Resources << /Font << /F1 5 0 R >> >> >>",
				"<< /Length " + contenido.length() + " >>\nstream\n" + contenido + "\nendstream",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>" };
		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		int[] posiciones = new int[objetos.length];
		for (int i = 0; i < objetos.length; i++) {
			posiciones[i] = pdf.length();
			pdf.append(i + 1).append(" 0 obj\n").append(objetos[i]).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objetos.length + 1).append("\n0000000000 65535 f \n");
		for (int posicion : posiciones) {
			pdf.append(String.format("%010d 00000 n \n", posicion));
		}
		pdf.append("trailer\n<< /Size ").append(objetos.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
				.append(xref).append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
	}
}
