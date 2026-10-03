package com.tiendas.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

	List<Categoria> findAllByOrderByOrdenAscNombreAsc();

	List<Categoria> findByRubroIdOrderByOrdenAscNombreAsc(Long rubroId);
}
