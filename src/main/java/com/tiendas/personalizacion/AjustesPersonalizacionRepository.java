package com.tiendas.personalizacion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AjustesPersonalizacionRepository extends JpaRepository<AjustesPersonalizacion, Long> {

	Optional<AjustesPersonalizacion> findFirstByOrderByIdAsc();
}
