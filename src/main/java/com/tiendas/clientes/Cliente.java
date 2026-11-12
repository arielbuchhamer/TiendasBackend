package com.tiendas.clientes;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuenta de cliente. La da de alta el administrador (no hay registro público) y el cliente elige su contraseña con
 * el link de activación. El estado se cambia con su propio endpoint: llega en el JSON pero se ignora al recibir.
 */
@Getter
@Setter
@Entity
public class Cliente extends EntidadBase {

	/** Rol de Spring Security de una sesión de cliente (las de administrador son {@code ADMIN}). */
	public static final String ROL = "CLIENTE";

	@NotBlank
	@Email
	@Size(max = 160)
	private String email;

	@NotBlank
	@Size(max = 100)
	private String nombre;

	@NotBlank
	@Size(max = 100)
	private String apellido;

	/** CUIT o DNI, opcional; si viene, mismo formato que el documento del checkout (vacío se guarda como null). */
	@Pattern(regexp = "\\s*|[0-9A-Za-z.-]{5,20}", message = "documento inválido")
	private String documento;

	@Size(max = 160)
	private String razonSocial;

	@Size(max = 40)
	private String telefono;

	@Size(max = 100)
	private String provincia;

	@Size(max = 100)
	private String localidad;

	/** Notas internas del administrador. */
	@Size(max = 1000)
	private String notas;

	@JsonIgnore
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@JsonProperty(access = Access.READ_ONLY)
	private EstadoCliente estado = EstadoCliente.PENDIENTE;

	/** Último envío de la invitación. */
	@JsonProperty(access = Access.READ_ONLY)
	private Instant invitadoEn;

	/** Último inicio de sesión correcto. */
	@JsonProperty(access = Access.READ_ONLY)
	private Instant ultimoAcceso;

	/** Columna generada por la base para la búsqueda del panel. */
	@JsonIgnore
	@Column(insertable = false, updatable = false)
	private String busqueda;

	/** Para el panel: si ya eligió su contraseña (la invitación se usó). */
	@JsonProperty(access = Access.READ_ONLY)
	public boolean isTieneClave() {
		return passwordHash != null;
	}

	boolean estaActivo() {
		return estado == EstadoCliente.ACTIVO;
	}
}
