package com.tiendas.pedidos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	Optional<Pago> findByProveedorAndIdExterno(String proveedor, String idExterno);
}
