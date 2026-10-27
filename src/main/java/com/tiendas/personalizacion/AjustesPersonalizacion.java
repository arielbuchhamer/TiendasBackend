package com.tiendas.personalizacion;

import java.math.BigDecimal;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Personalización que la tienda ofrece en todos sus productos (salvo los servicios). Hay una sola fila; si no
 * existe, valen los valores por defecto (el dueño los cambia en el panel).
 */
@Getter
@Setter
@Entity
public class AjustesPersonalizacion extends EntidadBase {

	/** Cómo se llama para el cliente (ej. "Grabado"). */
	@NotBlank
	@Size(max = 40)
	@Column(nullable = false)
	private String nombre = "Personalización";

	/** Se suma por unidad al precio del producto. */
	@NotNull
	@PositiveOrZero
	@Digits(integer = 12, fraction = 2)
	@Column(nullable = false)
	private BigDecimal precio = BigDecimal.ZERO;

	/** Qué pasa después de elegirla (ej. "Después de comprar, nos pasás el diseño por WhatsApp"). */
	@Size(max = 300)
	private String detalle;
}
