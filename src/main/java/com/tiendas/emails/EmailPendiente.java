package com.tiendas.emails;

import java.time.Instant;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** Email en la cola de salida. Lo crea y lo maneja solo el servidor; no se expone en la API. */
@Getter
@Setter
@Entity
class EmailPendiente extends EntidadBase {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoEmail tipo;

	private Long pedidoId;

	/** Direcciones separadas por coma. */
	@Column(nullable = false)
	private String destinatarios;

	@Column(name = "responder_a") // la estrategia de nombres no separa una mayúscula final
	private String responderA;

	@Column(nullable = false)
	private String asunto;

	@Column(nullable = false)
	private String html;

	@Column(nullable = false)
	private String texto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoEmail estado = EstadoEmail.PENDIENTE;

	private int intentos;

	@Column(nullable = false)
	private Instant proximoIntento = Instant.now();

	private String ultimoError;

	private String idExterno;

	private Instant enviadoEn;
}
