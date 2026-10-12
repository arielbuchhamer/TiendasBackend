package com.tiendas.pedidos;

/**
 * Evento: un pago aprobó el pedido y se descontó el stock. Se publica dentro de la transacción que aprueba
 * el pedido, así los listeners síncronos (ej. la cola de emails) guardan sus datos en esa misma transacción:
 * si la aprobación se revierte, también se revierte lo que hicieron.
 */
public record PedidoAprobado(Pedido pedido) {
}
