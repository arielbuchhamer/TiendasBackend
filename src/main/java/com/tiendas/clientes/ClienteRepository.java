package com.tiendas.clientes;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

	/** El email llega ya normalizado (trim + minúsculas), como se guarda. */
	Optional<Cliente> findByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	boolean existsByEmail(String email);
}
