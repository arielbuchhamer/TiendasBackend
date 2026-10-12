package com.tiendas.envios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaEnvioRepository extends JpaRepository<ZonaEnvio, Long> {

	List<ZonaEnvio> findAllByOrderByOrdenAscIdAsc();

	List<ZonaEnvio> findByActivaTrueOrderByOrdenAscIdAsc();
}
