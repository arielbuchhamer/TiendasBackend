package com.tiendas.correo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Correo Argentino de mentira para desarrollar sin credenciales: precios y plazos inventados (crecen con el peso y
 * la distancia entre códigos postales), algunas sucursales fijas y un seguimiento de ejemplo. Solo se crea en el
 * perfil local (ver {@link CorreoArgentinoConfig}).
 */
class MiCorreoSimulado implements CorreoArgentino {

	private static final Logger log = LoggerFactory.getLogger(MiCorreoSimulado.class);

	// Código postal de la capital de cada provincia, para que las sucursales inventadas coticen
	private static final Map<String, String> CP_CAPITALES = Map.ofEntries(
			Map.entry("A", "4400"), Map.entry("B", "1900"), Map.entry("C", "1000"), Map.entry("D", "5700"),
			Map.entry("F", "5300"), Map.entry("G", "4200"), Map.entry("H", "3500"), Map.entry("J", "5400"),
			Map.entry("K", "4700"), Map.entry("L", "6300"), Map.entry("M", "5500"), Map.entry("N", "3300"),
			Map.entry("P", "3600"), Map.entry("Q", "8300"), Map.entry("R", "8500"), Map.entry("S", "3000"),
			Map.entry("T", "4000"), Map.entry("U", "9103"), Map.entry("V", "9410"), Map.entry("W", "3400"),
			Map.entry("X", "5000"), Map.entry("Y", "4600"), Map.entry("Z", "9400"));

	private final int codigoPostalOrigen;

	MiCorreoSimulado(CorreoArgentinoProperties config) {
		this.codigoPostalOrigen = Integer.parseInt(config.codigoPostalOrigen().replaceAll("\\D", ""));
		log.warn("CORREO ARGENTINO SIMULADO: cotizaciones y sucursales inventadas (solo desarrollo local)");
	}

	@Override
	public List<Tarifa> cotizar(String codigoPostalDestino, List<Paquete> paquetes) {
		double kilos = paquetes.stream().mapToDouble(p -> p.cantidad() * p.pesoGramos() / 1000.0).sum();
		int distancia = Math.abs(Integer.parseInt(codigoPostalDestino) - codigoPostalOrigen);
		boolean lejos = distancia > 400;
		double domicilio = 3800 + 950 * Math.ceil(kilos) + Math.min(distancia, 6000) * 0.9;
		return List.of(
				tarifa("D", "CP", "Correo Argentino Clasico", domicilio, 2, lejos ? 6 : 4),
				tarifa("S", "CP", "Correo Argentino Clasico", domicilio * 0.8, 2, lejos ? 6 : 4),
				tarifa("D", "EP", "Correo Argentino Expreso", domicilio * 1.75, 1, lejos ? 3 : 2),
				tarifa("S", "EP", "Correo Argentino Expreso", domicilio * 1.6, 1, lejos ? 3 : 2));
	}

	private static Tarifa tarifa(String entrega, String servicio, String nombre, double precio, int min, int max) {
		return new Tarifa(entrega, servicio, nombre, BigDecimal.valueOf(precio).setScale(2, RoundingMode.HALF_UP), min, max);
	}

	@Override
	public List<Sucursal> sucursales(String codigoProvincia) {
		if ("E".equals(codigoProvincia)) {
			return List.of(
					new Sucursal("E0102", "Gral. Ramírez", "San Martín 450", "General Ramírez", "Entre Ríos", "3164"),
					new Sucursal("E0101", "Macía", "Rivadavia 120", "Macía", "Entre Ríos", "3177"),
					new Sucursal("E0001", "Paraná Centro", "Monte Caseros 175", "Paraná", "Entre Ríos", "3100"),
					new Sucursal("E0201", "Concepción del Uruguay", "Galarza 801", "Concepción del Uruguay", "Entre Ríos", "3260"));
		}
		String provincia = Provincias.nombre(codigoProvincia);
		String cp = CP_CAPITALES.getOrDefault(codigoProvincia, "5000");
		return List.of(
				new Sucursal(codigoProvincia + "0001", provincia + " Centro", "Av. San Martín 100", provincia, provincia, cp),
				new Sucursal(codigoProvincia + "0002", provincia + " Norte", "Belgrano 2500", provincia, provincia, cp));
	}

	@Override
	public void darDeAlta(AltaEnvio envio) {
		log.info("Correo simulado: alta del envío {} ({} {}) para {}", envio.idExterno(), envio.entrega(), envio.servicio(),
				envio.destinatario());
	}

	@Override
	public List<EventoSeguimiento> seguimiento(String codigoSeguimiento) {
		return List.of(
				new EventoSeguimiento("14-10-2026 11:20", "EN CAMINO", "CTP PARANA"),
				new EventoSeguimiento("13-10-2026 16:05", "ADMITIDO", "MACIA"),
				new EventoSeguimiento("13-10-2026 09:40", "PREIMPOSICION", "CORREO ARGENTINO"));
	}
}
