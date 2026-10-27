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
import jakarta.validation.constraints.Size;
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

	/** Si el cliente pidió personalizar el producto (ej. grabado). Su precio va incluido en el precio unitario. */
	private boolean personalizado;

	/** Lo que el cliente quiere (ej. el nombre a grabar). Opcional: también se puede coordinar después. */
	@Size(max = 300)
	private String personalizacionDetalle;

	@JsonProperty(access = Access.READ_ONLY)
	private String personalizacionNombre;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal personalizacionPrecio;

	/** Precio del producto más el de la personalización, si la tiene. */
	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal precioUnitario;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal subtotal;

	/** Nombre con la variante y la personalización (ej. "Mate (Rojo) + Grabado"), para Mercado Pago y los emails. */
	public String titulo() {
		return productoNombre
				+ (varianteDescripcion == null || varianteDescripcion.isBlank() ? "" : " (" + varianteDescripcion + ")")
				+ (personalizado ? " + " + personalizacionNombre : "");
	}
}
