package com.tiendas.comun;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Id y fechas de auditoría comunes a todas las entidades. Las fechas las maneja el servidor: se devuelven
 * en el JSON pero se ignoran si llegan en un request.
 * <p>
 * El id sí se lee del request, pero solo para identificar elementos de una colección al actualizar
 * (ej. qué variante se modifica). Regla del proyecto: los services nunca persisten la entidad recibida
 * en el body; siempre copian los campos permitidos sobre una entidad nueva o cargada de la base.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private Instant creadoEn;

	@UpdateTimestamp
	@Column(nullable = false)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private Instant actualizadoEn;
}
