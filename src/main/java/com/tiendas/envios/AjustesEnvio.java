package com.tiendas.envios;

import java.math.BigDecimal;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Ajustes generales de entrega de la tienda. Hay una sola fila; si no existe, valen los valores por defecto. */
@Getter
@Setter
@Entity
public class AjustesEnvio extends EntidadBase {

	private boolean retiroHabilitado = true;

	/** Dónde y cómo se retira, para mostrarle al cliente. */
	@Size(max = 300)
	private String retiroDetalle;

	/** Si el subtotal llega a este monto, el envío a domicilio es gratis (no aplica a zonas a convenir). */
	@Positive
	@Digits(integer = 12, fraction = 2)
	private BigDecimal envioGratisDesde;
}
