package com.tiendas.descuentos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Código de descuento que el cliente escribe en el checkout. Las condiciones (vigencia, mínimo, usos,
 * alcance) se validan en el servidor al crear el pedido; el frontend solo envía el código.
 * <p>
 * Un uso cuenta recién cuando el pedido se paga: los pedidos pendientes, vencidos o rechazados no
 * consumen usos. Por eso, si dos clientes pagan a la vez el último uso disponible, ambos pedidos se
 * aprueban (el cliente ya pagó con el descuento): se acepta ese excedente mínimo a cambio de no bloquear.
 * <p>
 * Las colecciones del alcance son LAZY; {@link CuponService} las inicializa antes de devolver el cupón.
 */
@Getter
@Setter
@Entity
public class Cupon extends EntidadBase {

	@NotBlank
	@Pattern(regexp = "\\s*[A-Za-z0-9_-]{3,40}\\s*", message = "solo letras, números, guiones; de 3 a 40 caracteres")
	private String codigo;

	/** Nota interna para el panel (ej. "Campaña Día del Padre"). El cliente no la ve. */
	@Size(max = 200)
	private String descripcion;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoCupon tipo;

	@Positive
	@Digits(integer = 12, fraction = 2)
	private BigDecimal valor;

	@Positive
	@Digits(integer = 12, fraction = 2)
	private BigDecimal tope;

	@Positive
	@Digits(integer = 12, fraction = 2)
	private BigDecimal minimoCompra;

	private Instant desde;

	private Instant hasta;

	@Positive
	private Integer usosMaximos;

	@Positive
	private Integer usosPorCliente;

	private boolean soloPrimeraCompra;

	private boolean activo = true;

	// ─── Alcance: si las tres están vacías, aplica a toda la tienda ─────

	@ElementCollection
	@CollectionTable(name = "cupon_rubro", joinColumns = @JoinColumn(name = "cupon_id"))
	@Column(name = "rubro_id", nullable = false)
	@Size(max = 200)
	private Set<Long> rubros = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "cupon_categoria", joinColumns = @JoinColumn(name = "cupon_id"))
	@Column(name = "categoria_id", nullable = false)
	@Size(max = 200)
	private Set<Long> categorias = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "cupon_producto", joinColumns = @JoinColumn(name = "cupon_id"))
	@Column(name = "producto_id", nullable = false)
	@Size(max = 500)
	private Set<Long> productos = new HashSet<>();

	// ─── Métricas para el panel (calculadas con los pedidos pagados) ────

	@Transient
	@JsonProperty(access = Access.READ_ONLY)
	private long usos;

	@Transient
	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal totalVendido = BigDecimal.ZERO;

	@Transient
	@JsonProperty(access = Access.READ_ONLY)
	private BigDecimal totalDescontado = BigDecimal.ZERO;

	public boolean aplicaATodaLaTienda() {
		return rubros.isEmpty() && categorias.isEmpty() && productos.isEmpty();
	}
}
