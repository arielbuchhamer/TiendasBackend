package com.tiendas.clientes;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Link de activación o de cambio de contraseña. Interno: nunca sale en el JSON. Ver {@link TokensCliente}. */
@Getter
@Setter
@Entity
class ClienteToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long clienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoTokenCliente tipo;

	/** SHA-256 en hexadecimal del token que viaja en el link. */
	@Column(nullable = false)
	private String hash;

	@Column(nullable = false)
	private Instant venceEn;

	private Instant usadoEn;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant creadoEn;

	boolean vigente(Instant ahora) {
		return usadoEn == null && ahora.isBefore(venceEn);
	}
}
