package com.tiendas.envios;

import java.math.BigDecimal;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	private TipoZona tipo = TipoZona.PRECIO_FIJO;

	@Size(max = 500)
	private String codigosPostales;

	/**
	 * Provincias de la zona, con el código de una letra del Correo separado por comas (ej. "E,S,X"). Una zona con
	 * códigos postales se elige por código postal; si no tiene, por provincia; sin ninguno de los dos es "el resto
	 * del país".
	 */
	@Size(max = 100)
	private String provincias;

	/**
	 * Obligatorio salvo que el costo sea a convenir. En las zonas de Correo Argentino y de Zipnova es opcional: es
	 * el precio de respaldo si no se puede cotizar (sin precio, el costo queda a convenir).
	 */
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

	// Servicios que ofrece una zona de Correo Argentino o de Zipnova (los nombres vienen de cuando solo existía el
	// Correo): domicilio, retiro en sucursal y Expreso / express
	private boolean correoDomicilio = true;

	private boolean correoSucursal = true;

	private boolean correoExpreso;

	/** Orden de evaluación y de aparición en el panel (menor primero). */
	private int orden;
}
