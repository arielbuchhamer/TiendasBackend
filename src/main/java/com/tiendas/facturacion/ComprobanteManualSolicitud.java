package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Solicitud simplificada para el panel: solo cliente e items con precio final. Emisor, tipo, punto de
 * venta, moneda, IVA e importes los completa el servidor a partir de la configuración de la tienda.
 */
@Data
public class ComprobanteManualSolicitud {
	private String idempotencyKey;
	private LocalDate fecha;
	private ComprobanteSolicitud.DatosReceptor cliente;
	private List<Item> items = new ArrayList<>();

	@Data
	public static class Item {
		private String descripcion;
		private BigDecimal cantidad;
		private BigDecimal precioUnitario;
	}
}
