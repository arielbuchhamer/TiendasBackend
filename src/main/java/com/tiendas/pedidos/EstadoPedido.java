package com.tiendas.pedidos;

public enum EstadoPedido {
	/** Creado, esperando el pago. */
	PENDIENTE,
	/** Pago aprobado y stock descontado. */
	APROBADO,
	/** El pago fue rechazado o cancelado. */
	RECHAZADO,
	/** Pasó el plazo de pago sin que se apruebe. */
	VENCIDO,
	/** No se pudo iniciar el pago (falló Mercado Pago al crear la preferencia). */
	CANCELADO,
	/**
	 * Se cobró pero hay que revisar a mano: no había stock al aprobarse o el monto cobrado no coincide.
	 * El stock no se descuenta; el administrador decide si reponer o devolver el dinero.
	 */
	REQUIERE_REVISION,
	/** El pago aprobado fue devuelto o desconocido (contracargo). */
	REINTEGRADO
}
