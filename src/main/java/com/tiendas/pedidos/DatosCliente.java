package com.tiendas.pedidos;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos del comprador. La tienda no maneja cuentas de cliente: se guardan en cada pedido. */
@Getter
@Setter
@Embeddable
public class DatosCliente {

	@NotBlank
	@Size(max = 100)
	@Column(name = "cliente_nombre")
	private String nombre;

	@NotBlank
	@Size(max = 100)
	@Column(name = "cliente_apellido")
	private String apellido;

	@NotBlank
	@Pattern(regexp = "[0-9A-Za-z.-]{5,20}", message = "documento inválido")
	@Column(name = "cliente_documento")
	private String documento;

	@NotBlank
	@Email
	@Size(max = 160)
	@Column(name = "cliente_email")
	private String email;

	@Size(max = 40)
	@Column(name = "cliente_telefono")
	private String telefono;
}
