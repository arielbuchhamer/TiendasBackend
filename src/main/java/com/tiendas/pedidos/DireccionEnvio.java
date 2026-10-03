package com.tiendas.pedidos;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class DireccionEnvio {

	@NotBlank
	@Size(max = 120)
	@Column(name = "envio_calle")
	private String calle;

	@NotBlank
	@Size(max = 20)
	@Column(name = "envio_numero")
	private String numero;

	@Size(max = 10)
	@Column(name = "envio_piso")
	private String piso;

	@Size(max = 10)
	@Column(name = "envio_departamento")
	private String departamento;

	@NotBlank
	@Size(max = 80)
	@Column(name = "envio_ciudad")
	private String ciudad;

	@NotBlank
	@Size(max = 80)
	@Column(name = "envio_provincia")
	private String provincia;

	@NotBlank
	@Size(max = 12)
	@Column(name = "envio_codigo_postal")
	private String codigoPostal;
}
