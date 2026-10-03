package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de compra. En el checkout el frontend envía solo cliente, entrega, dirección e items
 * (variante y cantidad); todo lo demás (precios, totales, estado) lo calcula el servidor y se ignora
 * si llega en el request.
 */
@Getter
@Setter
@Entity
public class Pedido extends EntidadBase {

	@JsonProperty(access = Access.READ_ONLY)
	@Column(nullable = false, updatable = false)
	private UUID codigo;

	@JsonProperty(access = Access.READ_ONLY)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoPedido estado;

	@Valid
	@NotNull
	@Embedded
	private DatosCliente cliente;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoEntrega entrega;

	/** Obligatoria si la entrega es a domicilio. */
	@Valid
	@Embedded
	private DireccionEnvio direccion;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal subtotal;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal costoEnvio;

	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal total;

	@JsonProperty(access = Access.READ_ONLY)
	private String moneda;

	@JsonIgnore
	private String mpPreferenceId;

	@JsonProperty(access = Access.READ_ONLY)
	private String checkoutUrl;

	@JsonProperty(access = Access.READ_ONLY)
	private boolean visto;

	@JsonProperty(access = Access.READ_ONLY)
	private Instant aprobadoEn;

	@Valid
	@NotEmpty
	@Size(max = 50)
	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("posicion")
	private List<PedidoItem> items = new ArrayList<>();

	@JsonProperty(access = Access.READ_ONLY)
	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
	@OrderBy("creadoEn")
	private List<Pago> pagos = new ArrayList<>();

	public void agregarItem(PedidoItem item) {
		item.setPedido(this);
		item.setPosicion(items.size());
		items.add(item);
	}

	public void agregarPago(Pago pago) {
		pago.setPedido(this);
		pagos.add(pago);
	}
}
