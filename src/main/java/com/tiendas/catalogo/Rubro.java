package com.tiendas.catalogo;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Primer nivel de clasificación del catálogo (ej. "Celulares", "Maderas macizas"). */
@Getter
@Setter
@Entity
public class Rubro extends EntidadBase {

	@NotBlank
	@Size(max = 80)
	private String nombre;

	/** Orden de aparición en el frontend (menor primero). */
	private int orden;
}
