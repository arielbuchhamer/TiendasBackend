package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proveedor de cobros online. Hoy la única implementación es Mercado Pago; la interfaz deja el
 * dominio de pedidos independiente del SDK y permite simular el proveedor en los tests.
 */
public interface PasarelaPago {

	/** Crea el checkout externo para un pedido ya guardado. */
	CheckoutExterno crearCheckout(Pedido pedido);

	/** @param referencia id del checkout en el proveedor; @param url adonde se redirige al comprador */
	record CheckoutExterno(String referencia, String url) {
	}

	/** Estado de un pago informado por el proveedor, ya normalizado. */
	enum ResultadoPago {
		APROBADO, RECHAZADO, REINTEGRADO, PENDIENTE
	}

	/**
	 * Pago consultado al proveedor.
	 *
	 * @param referenciaPedido código del pedido (external_reference)
	 * @param estado           estado original del proveedor, para auditoría
	 */
	record PagoInformado(
			String proveedor,
			String idExterno,
			String referenciaPedido,
			ResultadoPago resultado,
			String estado,
			String estadoDetalle,
			BigDecimal monto,
			String moneda,
			String medioPago,
			Instant aprobadoEn) {
	}
}
