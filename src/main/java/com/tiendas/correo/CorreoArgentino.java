package com.tiendas.correo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la tienda usa de Correo Argentino. Implementaciones: {@link MiCorreoApi} (la API real) y
 * {@link MiCorreoSimulado} (solo desarrollo local).
 */
public interface CorreoArgentino {

	/** Un bulto (o varios iguales, con {@code cantidad}). Peso en gramos y medidas en centímetros. */
	record Paquete(int cantidad, int pesoGramos, int largoCm, int anchoCm, int altoCm) {
	}

	/**
	 * Una cotización.
	 *
	 * @param entrega  "D" domicilio o "S" sucursal
	 * @param servicio "CP" Clásico o "EP" Expreso
	 */
	record Tarifa(String entrega, String servicio, String nombre, BigDecimal precio, Integer diasMin, Integer diasMax) {
	}

	record Sucursal(String codigo, String nombre, String direccion, String localidad, String provincia,
			String codigoPostal) {

		/** Texto para mostrar y guardar en el pedido, ej. "Macía - Rivadavia 123, Macía (CP 3177)". */
		public String detalle() {
			return nombre + " - " + direccion + ", " + localidad + (codigoPostal == null ? "" : " (CP " + codigoPostal + ")");
		}
	}

	/** Datos para dar de alta un envío en MiCorreo. {@code sucursal} solo si la entrega es "S". */
	record AltaEnvio(String idExterno, String numeroPedido, String destinatario, String email, String telefono,
			String entrega, String servicio, String sucursal, String calle, String numero, String piso,
			String departamento, String ciudad, String provincia, String codigoPostal, Paquete paquete,
			BigDecimal valorDeclarado) {
	}

	record EventoSeguimiento(String fecha, String evento, String lugar) {
	}

	/** Cotizaciones (domicilio y sucursal, Clásico y Expreso) para un código postal de destino. */
	List<Tarifa> cotizar(String codigoPostalDestino, List<Paquete> paquetes);

	/** Sucursales activas de una provincia donde se puede retirar un envío (código de provincia de una letra). */
	List<Sucursal> sucursales(String codigoProvincia);

	/** Da de alta el envío en MiCorreo: queda en "Mis envíos" listo para pagar e imprimir el rótulo. */
	void darDeAlta(AltaEnvio envio);

	/** Eventos del seguimiento de un envío, del más nuevo al más viejo. */
	List<EventoSeguimiento> seguimiento(String codigoSeguimiento);
}
