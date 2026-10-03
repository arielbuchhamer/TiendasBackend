package com.tiendas.facturacion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

	Optional<Comprobante> findByIdempotencyKey(String idempotencyKey);

	Optional<Comprobante> findByCodigo(UUID codigo);
}
