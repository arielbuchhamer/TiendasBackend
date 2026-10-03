package com.tiendas.catalogo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Variante vendible de un producto. Los atributos son libres para que sirvan a cualquier rubro:
 * {@code {"color": "Negro"}} en una tienda de celulares, {@code {"espesor": "2\"", "largo": "3,05 m"}}
 * en una maderera.
 */
@Getter
@Setter
@Entity
public class Variante extends EntidadBase {

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id")
	private Producto producto;

	@JsonIgnore
	private int posicion;

	/** Código único. Si llega vacío, el servidor genera uno. */
	@Size(max = 60)
	@Pattern(regexp = "[A-Za-z0-9._-]*", message = "solo letras, números y . _ -")
	private String sku;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	@Size(max = 10)
	private Map<String, String> atributos = new LinkedHashMap<>();

	/** Si es null se usa el precio del producto. */
	@PositiveOrZero
	@Digits(integer = 12, fraction = 2)
	private BigDecimal precio;

	@NotNull
	@PositiveOrZero
	@Digits(integer = 11, fraction = 3)
	private BigDecimal stock = BigDecimal.ZERO;

	private UUID imagenId;

	private boolean activo = true;

	@Version
	private Long version;

	public BigDecimal precioEfectivo() {
		return precio != null ? precio : producto.getPrecio();
	}

	/** Texto legible de los atributos, ej. "Negro / 128 GB". Vacío si la variante no tiene atributos. */
	public String descripcion() {
		return String.join(" / ", atributos.values());
	}
}
