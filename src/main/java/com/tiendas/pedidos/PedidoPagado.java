package com.tiendas.pedidos;

/**
 * Se publica cuando un pago aprueba un pedido (una sola vez por pedido). Los interesados (ej. las
 * notificaciones por mail) lo escuchan después del commit, así nunca actúan sobre un pago que se revirtió.
 *
 * @param requiereRevision el pago llegó pero el pedido quedó en revisión (monto distinto o sin stock)
 */
public record PedidoPagado(Long pedidoId, boolean requiereRevision) {
}
