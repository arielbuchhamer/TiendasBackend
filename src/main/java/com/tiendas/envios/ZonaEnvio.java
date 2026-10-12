package com.tiendas.envios;

import java.math.BigDecimal;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Zona de envío a domicilio: los códigos postales a los que llega, el precio y el plazo.
 * <p>
 * {@code codigosPostales} es una lista separada por comas de códigos (4 dígitos) o rangos, ej.
 * {@code "3164, 3100-3299"}. Una zona sin códigos es "el resto del país": se usa solo si ninguna otra coincide.
 */
@Getter
@Setter
@Entity
public class ZonaEnvio extends EntidadBase {

	@NotBlank
	@Size(max = 80)
	private String nombre;

	@Size(max = 500)
	private String codigosPostales;

	/** Obligatorio salvo que el costo sea a convenir. */
	@PositiveOrZero
	@Digits(integer = 12, fraction = 2)
	private BigDecimal precio;

	/** El costo no se cobra en la web: se arregla con el cliente después de la compra. */
	@Column(name = "costo_a_convenir") // la estrategia de nombres no separa "AConvenir"
	private boolean costoAConvenir;

	@PositiveOrZero
	@Max(90)
	private Integer diasMin;

	@PositiveOrZero
	@Max(90)
	private Integer diasMax;

	private boolean activa = true;

	/** Orden de evaluación y de aparición en el panel (menor primero). */
	private int orden;
}
