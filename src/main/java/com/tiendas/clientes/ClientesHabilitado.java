package com.tiendas.clientes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de cuentas de cliente: solo se crean si {@code clientes.habilitada=true}. Sin el
 * módulo no existen los endpoints de clientes y el checkout sigue siendo anónimo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ConditionalOnBooleanProperty("clientes.habilitada")
@interface ClientesHabilitado {
}
