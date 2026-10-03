package com.tiendas.catalogo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.tiendas.comun.EntidadBase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Producto del catálogo. Siempre tiene al menos una {@link Variante}: un producto "simple" tiene una
 * sola variante sin atributos. El stock vive únicamente en las variantes.
 * <p>
 * Las colecciones son LAZY; los services las inicializan antes de devolver el producto (en lote, gracias
 * a {@code hibernate.default_batch_fetch_size}), así el JSON nunca dispara consultas fuera de la transacción.
 */
@Getter
@Setter
@Entity
public class Producto extends EntidadBase {

	@NotNull
	@Column(nullable = false)
	private Long categoriaId;

	@NotBlank
	@Size(max = 200)
	private String nombre;

	@Size(max = 10_000)
	private String descripcion;

	/** Precio base. Una variante puede definir su propio precio. */
	@NotNull
	@PositiveOrZero
	@Digits(integer = 12, fraction = 2)
	private BigDecimal precio;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UnidadVenta unidadVenta = UnidadVenta.UNIDAD;

	private boolean activo = true;

	private boolean destacado;

	/** Control de concurrencia optimista: el panel debe reenviar la versión que leyó. */
	@Version
	private Long version;

	/** Ids de imágenes en orden de aparición; la primera es la principal. */
	@ElementCollection
	@CollectionTable(name = "producto_imagen", joinColumns = @JoinColumn(name = "producto_id"))
	@OrderColumn(name = "posicion")
	@Column(name = "imagen_id", nullable = false)
	@Size(max = 20)
	private List<UUID> imagenes = new ArrayList<>();

	@Valid
	@NotEmpty
	@Size(max = 200)
	@OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("posicion")
	private List<Variante> variantes = new ArrayList<>();
}
