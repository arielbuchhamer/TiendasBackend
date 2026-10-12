package com.tiendas.envios;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de envíos que solo existen si {@code envios.habilitada=true} (los endpoints).
 * {@link EnvioService} existe siempre porque lo usa el checkout, que decide según la propiedad.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("envios.habilitada")
@interface EnviosHabilitada {
}
