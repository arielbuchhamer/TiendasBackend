package com.tiendas.usuarios;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tiendas.comun.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Usuario extends EntidadBase {

	@NotBlank
	@Pattern(regexp = "[a-zA-Z0-9._@-]{3,60}", message = "solo letras, números y . _ @ - (3 a 60 caracteres)")
	private String username;

	@NotBlank
	@Size(max = 120)
	private String nombre;

	@JsonIgnore
	@Column(nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Rol rol = Rol.ADMIN;

	private boolean activo = true;

	/** Clave en texto plano: solo entra en requests de alta/modificación, nunca se persiste ni se devuelve. */
	@Transient
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Size(min = 10, max = 72, message = "debe tener entre 10 y 72 caracteres")
	private String clave;
}
