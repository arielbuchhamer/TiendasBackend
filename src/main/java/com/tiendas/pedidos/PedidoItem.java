package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.tiendas.catalogo.UnidadVenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Línea de un pedido. Guarda una copia (snapshot) de nombre, SKU y precio al momento de la compra:
 * si el producto cambia o se borra después, el pedido sigue mostrando lo que realmente se vendió.
 */
@Getter
@Setter
@Entity
public class PedidoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty(access = Access.READ_ONLY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id")
	private Pedido pedido;

	@JsonIgnore
	private int posicion;

	@NotNull
	@Column(nullable = false)
	private Long varianteId;

	@NotNull
	@Positive
	@Digits(integer = 11, fraction = 3)
	private BigDecimal cantidad;

	@JsonProperty(access = Access.READ_ONLY)
	private String productoNombre;

	@JsonProperty(access = Access.READ_ONLY)
	private String varianteDescripcion;

	@JsonProperty(access = Access.READ_ONLY)
	private String sku;

	@JsonProperty(access = Access.READ_ONLY)
	@Enumerated(EnumType.STRING)
	private UnidadVenta unidadVenta;

	@JsonProperty(access = Access.READ_ONLY)
	private UUID imagenId;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal precioUnitario;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal subtotal;
}
