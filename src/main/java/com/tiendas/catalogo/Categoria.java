package com.tiendas.catalogo;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Segundo nivel de clasificación, dentro de un rubro. */
@Getter
@Setter
@Entity
public class Categoria extends EntidadBase {

	@NotNull
	@Column(nullable = false)
	private Long rubroId;

	@NotBlank
	@Size(max = 80)
	private String nombre;

	private int orden;
}
