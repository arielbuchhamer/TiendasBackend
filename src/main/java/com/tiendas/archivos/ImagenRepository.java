package com.tiendas.archivos;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenRepository extends JpaRepository<Imagen, UUID> {
}
