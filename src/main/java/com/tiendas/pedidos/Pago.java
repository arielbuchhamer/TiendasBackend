package com.tiendas.pedidos;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** Intento de pago informado por el proveedor (hoy Mercado Pago). Lo registra solo el servidor. */
@Getter
@Setter
@Entity
public class Pago extends EntidadBase {

	public static final String MERCADO_PAGO = "MERCADO_PAGO";

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id")
	private Pedido pedido;

	@Column(nullable = false)
	private String proveedor;

	/** Id del pago en el proveedor (payment id de Mercado Pago). */
	@Column(nullable = false)
	private String idExterno;

	/** Estado tal como lo informa el proveedor (approved, rejected, refunded...). */
	@Column(nullable = false)
	private String estado;

	private String estadoDetalle;

	private BigDecimal monto;

	private String moneda;

	private String medioPago;

	private Instant aprobadoEn;
}
