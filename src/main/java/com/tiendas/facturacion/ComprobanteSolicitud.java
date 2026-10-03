package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Datos completos de un comprobante a emitir. Se guarda como JSONB en {@link Comprobante} para que el
 * PDF y la auditoría reflejen exactamente lo que se envió a ARCA.
 */
@Data
public class ComprobanteSolicitud {
	private String idempotencyKey;
	private DatosEmisor emisor;
	private DatosReceptor cliente;
	private TipoComprobante tipoComprobante;
	private Integer puntoVenta;
	private Integer concepto;
	private LocalDate fecha;
	private String moneda;
	private BigDecimal cotizacion;
	private List<Item> items = new ArrayList<>();
	private Importes importes;
	private List<Asociado> comprobantesAsociados = new ArrayList<>();

	@Data
	public static class DatosEmisor {
		private String cuit;
		private String razonSocial;
		private CondicionIva condicionIva;
		private String domicilio;
		private String ingresosBrutos;
		private LocalDate inicioActividades;
	}

	@Data
	public static class DatosReceptor {
		private String nombre;
		private String apellido;
		private String razonSocial;
		private TipoDocumento tipoDocumento;
		private String numeroDocumento;
		private CondicionIva condicionIva;
		private String domicilio;
		private String email;
		private String telefono;
	}

	@Data
	public static class Item {
		private String descripcion;
		private BigDecimal cantidad;
		private BigDecimal precioUnitario;
		private AlicuotaIva alicuotaIva;
		private BigDecimal importeIva;
		private BigDecimal subtotal;
	}

	@Data
	public static class Importes {
		private BigDecimal neto;
		private BigDecimal iva;
		private BigDecimal tributos;
		private BigDecimal exento;
		private BigDecimal noGravado;
		private BigDecimal total;
	}

	@Data
	public static class Asociado {
		private TipoComprobante tipoComprobante;
		private Integer puntoVenta;
		private Long numero;
		private String cuit;
		private LocalDate fecha;
	}
}
