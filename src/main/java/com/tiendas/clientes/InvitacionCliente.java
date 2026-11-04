package com.tiendas.clientes;

import java.time.Duration;

/**
 * Evento: el administrador invitó a un cliente a activar su cuenta. Se publica dentro de la transacción que guardó
 * el link, así el email se encola si y solo si el link quedó guardado.
 *
 * @param link URL con el token en claro: solo viaja en el email
 */
public record InvitacionCliente(Long clienteId, String email, String nombre, String link, Duration vigencia) {
}
