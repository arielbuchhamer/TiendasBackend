package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** Comprobante fiscal emitido (o intentado) ante ARCA. Solo lo crea el servidor. */
@Getter
@Setter
@Entity
public class Comprobante extends EntidadBase {

	/** Identificador público: permite compartir el PDF con el comprador sin exponer el id interno. */
	@Column(nullable = false, updatable = false)
	private UUID codigo;

	@Column(nullable = false, updatable = false)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoComprobante estado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoComprobante tipo;

	@Column(nullable = false)
	private Integer puntoVenta;

	private Long numero;

	@Column(nullable = false)
	private BigDecimal total;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private ComprobanteSolicitud solicitud;

	private String cae;

	private LocalDate caeVencimiento;

	private String resultado;

	/** Request y respuesta crudos de AFRelay, para auditoría y soporte. */
	@JsonIgnore
	@JdbcTypeCode(SqlTypes.JSON)
	private String afrelayRequest;

	@JsonIgnore
	@JdbcTypeCode(SqlTypes.JSON)
	private String afrelayResponse;

	@JsonIgnore
	private String pdfClave;

	private String error;

	private Instant autorizadoEn;

	/** Ruta pública del PDF (relativa a la URL de la API); null si el comprobante no está autorizado. */
	public String getPdfRuta() {
		return estado == EstadoComprobante.AUTORIZADO ? "/api/v1/comprobantes/" + codigo + "/pdf" : null;
	}

	public String numeroCompleto() {
		return String.format("%04d-%08d", puntoVenta, numero == null ? 0 : numero);
	}
}
