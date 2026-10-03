package com.tiendas.facturacion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de facturación: solo se crean si {@code facturacion.habilitada=true}.
 * Una tienda que no factura electrónicamente no necesita configurar nada de ARCA ni AFRelay.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("facturacion.habilitada")
@interface FacturacionHabilitada {
}
