package com.tiendas.clientes;

import java.time.Instant;

/**
 * Lo que ve el cliente de su propia cuenta (login y {@code GET /clientes/yo}). No es la entidad porque esta lleva
 * datos que son solo para el administrador ({@code notas}, {@code invitadoEn}).
 */
record CuentaCliente(Long id, String email, String nombre, String apellido, String documento, String razonSocial,
		String telefono, String provincia, String localidad, EstadoCliente estado, Instant ultimoAcceso,
		Instant creadoEn) {

	static CuentaCliente de(Cliente c) {
		return new CuentaCliente(c.getId(), c.getEmail(), c.getNombre(), c.getApellido(), c.getDocumento(),
				c.getRazonSocial(), c.getTelefono(), c.getProvincia(), c.getLocalidad(), c.getEstado(),
				c.getUltimoAcceso(), c.getCreadoEn());
	}
}
