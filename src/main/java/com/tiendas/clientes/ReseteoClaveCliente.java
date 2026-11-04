package com.tiendas.clientes;

import java.time.Duration;

/**
 * Evento: un cliente activo pidió elegir una contraseña nueva. Se publica dentro de la transacción que guardó el
 * link, como {@link InvitacionCliente}.
 */
public record ReseteoClaveCliente(Long clienteId, String email, String nombre, String link, Duration vigencia) {
}
