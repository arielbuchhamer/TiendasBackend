package com.tiendas.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

	// ORDER BY random() recorre todos los activos: es razonable para catálogos de miles de productos
	// y el resultado se cachea (ver ProductoService#aleatorios)
	@Query(value = "SELECT id FROM producto WHERE activo ORDER BY random() LIMIT :cantidad", nativeQuery = true)
	List<Long> idsAleatorios(int cantidad);
}
