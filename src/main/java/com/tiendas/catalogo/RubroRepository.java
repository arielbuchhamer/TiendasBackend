package com.tiendas.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RubroRepository extends JpaRepository<Rubro, Long> {

	List<Rubro> findAllByOrderByOrdenAscNombreAsc();
}
