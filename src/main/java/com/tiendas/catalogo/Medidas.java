package com.tiendas.catalogo;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Peso y medidas del paquete listo para despachar. Los correos cotizan con estos datos. En una
 * {@link Categoria} son el valor por defecto de sus productos; un {@link Producto} puede definir los suyos.
 * Límites de Correo Argentino: 25 kg y 150 cm por lado.
 */
@Getter
@Setter
@Embeddable
public class Medidas {

	@Positive
	@Max(25_000)
	private Integer pesoGramos;

	@Positive
	@Max(150)
	private Integer largoCm;

	@Positive
	@Max(150)
	private Integer anchoCm;

	@Positive
	@Max(150)
	private Integer altoCm;

	/** Copia nula si no viene ningún dato (Hibernate guarda un embebido vacío como null). */
	public static Medidas copiar(Medidas datos) {
		if (datos == null || (datos.pesoGramos == null && datos.largoCm == null && datos.anchoCm == null
				&& datos.altoCm == null)) {
			return null;
		}
		Medidas copia = new Medidas();
		copia.pesoGramos = datos.pesoGramos;
		copia.largoCm = datos.largoCm;
		copia.anchoCm = datos.anchoCm;
		copia.altoCm = datos.altoCm;
		return copia;
	}
}
