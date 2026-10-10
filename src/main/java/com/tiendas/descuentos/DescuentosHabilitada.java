package com.tiendas.descuentos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de descuentos que solo existen si {@code descuentos.habilitada=true}
 * (los endpoints del panel). El checkout siempre conoce los cupones, pero los rechaza si el módulo
 * está apagado.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("descuentos.habilitada")
@interface DescuentosHabilitada {
}
