package com.tiendas.personalizacion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de personalización que solo existen si {@code personalizacion.habilitada=true}
 * (los endpoints). {@link PersonalizacionService} existe siempre porque lo usa el checkout, que rechaza los
 * productos personalizados si el módulo está apagado.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("personalizacion.habilitada")
@interface PersonalizacionHabilitada {
}
