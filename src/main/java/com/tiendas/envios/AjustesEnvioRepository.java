package com.tiendas.envios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AjustesEnvioRepository extends JpaRepository<AjustesEnvio, Long> {

	Optional<AjustesEnvio> findFirstByOrderByIdAsc();
}
