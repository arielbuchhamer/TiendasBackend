package com.tiendas.pedidos;

/**
 * Grupo de validación del checkout anónimo: ahí los datos del comprador llegan en el body y son obligatorios. En
 * el checkout de un cliente con sesión (módulo de clientes) salen de su cuenta, así que el body no los trae.
 */
public interface CheckoutAnonimo {
}
