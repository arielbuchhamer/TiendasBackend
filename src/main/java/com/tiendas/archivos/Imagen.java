package com.tiendas.archivos;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadatos de una imagen subida. El archivo se guarda en el almacenamiento en varios tamaños
 * (ver {@link TamanioImagen}) y se sirve en {@code /api/v1/imagenes/{id}/{tamanio}}.
 * El id es un UUID para que no se puedan enumerar.
 */
@Getter
@Setter
@Entity
public class Imagen {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String contentType;

	private int ancho;

	private int alto;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant creadoEn;
}
