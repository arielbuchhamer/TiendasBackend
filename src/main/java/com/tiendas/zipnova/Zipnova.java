package com.tiendas.zipnova;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la tienda usa de Zipnova. Implementaciones: {@link ZipnovaApi} (la API real) y {@link ZipnovaSimulado}
 * (solo desarrollo local).
 */
public interface Zipnova {

	/** Código de servicio de Zipnova para el retiro en una sucursal o punto de retiro. */
	String RETIRO_EN_PUNTO = "pickup_point";

	/** Una unidad de producto. Peso en gramos y medidas en centímetros; Zipnova arma los paquetes. */
	record Item(int pesoGramos, int largoCm, int anchoCm, int altoCm, String descripcion) {
	}

	/**
	 * Destino de una cotización. Zipnova identifica la localidad por nombre y provincia; la ciudad puede faltar
	 * (todavía no la escribió el cliente) y entonces Zipnova resuelve con el código postal.
	 */
	record Destino(String codigoPostal, String ciudad, String provincia) {
	}

	/** Sucursal o punto donde el cliente retira el paquete. */
	record Punto(String id, String nombre, String direccion, String ciudad, String provincia, String codigoPostal,
			String horario) {

		/** Texto para mostrar y guardar en el pedido, ej. "Andreani Paraná - Urquiza 1234, Paraná". */
		public String detalle() {
			String lugar = ciudad == null || ciudad.isBlank() ? "" : ", " + ciudad;
			return (nombre + (direccion == null || direccion.isBlank() ? "" : " - " + direccion) + lugar);
		}
	}

	/**
	 * Una forma de entrega con un transporte (ej. OCA a domicilio, Correo Argentino a sucursal).
	 *
	 * @param servicio        código del servicio de Zipnova (ej. "standard_delivery", "pickup_point"); se usa al crear
	 * @param transportistaId id del transporte en Zipnova; se usa al crear
	 * @param precio          lo que paga el comprador, con IVA y seguro
	 * @param logistica       forma de despacho (ej. "carrier_dropoff": se lleva el paquete al transporte); se usa al crear
	 * @param entregaEstimada fecha estimada de entrega (ISO, ej. "2026-10-23"), si Zipnova la informa
	 * @param puntos          sucursales cercanas al destino, solo en el retiro en punto
	 */
	record Opcion(String servicio, String servicioNombre, boolean urgente, long transportistaId, String transportista,
			String logo, String logistica, BigDecimal precio, Integer diasMin, Integer diasMax, String entregaEstimada,
			List<Punto> puntos) {

		public boolean retiroEnPunto() {
			return RETIRO_EN_PUNTO.equals(servicio);
		}
	}

	/**
	 * Datos para dar de alta un envío. {@code puntoId} solo si el servicio es el retiro en punto; {@code logistica}
	 * es la que trajo la cotización elegida (si falta, la elige Zipnova).
	 */
	record AltaEnvio(String idExterno, String servicio, long transportistaId, String logistica, String puntoId,
			String destinatario,
			String documento, String email, String telefono, String calle, String numero, String extras,
			String ciudad, String provincia, String codigoPostal, List<Item> items, BigDecimal valorDeclarado) {
	}

	/** Un envío ya creado en Zipnova. */
	record Envio(String id, String estado, String transportista, String codigoSeguimiento, String urlSeguimiento) {
	}

	record EventoSeguimiento(String fecha, String evento, String lugar) {
	}

	/** Saldo de la cuenta de Zipnova: los envíos se cobran de acá. {@code disponible} = saldo + crédito. */
	record Saldo(BigDecimal saldo, BigDecimal limiteCredito, BigDecimal disponible) {
	}

	/** Resultado de cancelar: "cancelado" o "rescate" (ya estaba despachado y se pidió al transporte que lo frene). */
	record Cancelacion(boolean rescate) {
	}

	/**
	 * Resultado de una cotización: el destino tal como lo entendió Zipnova (identifica la localidad por nombre, así
	 * que conviene mostrárselo al cliente) y las opciones.
	 */
	record Cotizacion(String localidad, String provincia, List<Opcion> opciones) {

		/** "Reconquista, Santa Fe" (vacío si Zipnova no lo informó). */
		public String destino() {
			return localidad == null || localidad.isBlank() ? null
					: localidad + (provincia == null || provincia.isBlank() ? "" : ", " + provincia);
		}
	}

	/** Todas las opciones seleccionables para un destino: cada forma de entrega con cada transporte habilitado. */
	Cotizacion cotizar(Destino destino, List<Item> items, BigDecimal valorDeclarado);

	/** Crea el envío en Zipnova: queda listo para imprimir la etiqueta y despachar. Lo paga la cuenta de Zipnova. */
	Envio darDeAlta(AltaEnvio envio);

	/** Estado actual de un envío. */
	Envio envio(String id);

	/** Etiqueta del envío en PDF (una página por paquete). */
	byte[] etiqueta(String id);

	/** Historial de estados del envío, del más nuevo al más viejo. */
	List<EventoSeguimiento> seguimiento(String id);

	/**
	 * Guía de despacho en PDF (algunos transportes la exigen pegada a la factura o remito). Vacío si el transporte
	 * del envío no usa guía: alcanza con la etiqueta.
	 */
	java.util.Optional<byte[]> guia(String id);

	/** Cancela el envío; si ya fue despachado, pide el rescate al transporte. */
	Cancelacion cancelar(String id);

	/** Dónde llevar el paquete: los puntos del transporte cercanos al origen (envíos de tipo dropoff). */
	List<Punto> puntosDeDespacho(String id);

	Saldo saldo();
}
