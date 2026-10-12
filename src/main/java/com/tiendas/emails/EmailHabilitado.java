package com.tiendas.emails;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marca los beans del módulo de emails: solo se crean si {@code email.habilitada=true}. Sin el módulo, nadie
 * escucha {@link com.tiendas.pedidos.PedidoAprobado} y no se encola nada.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("email.habilitada")
@interface EmailHabilitado {
}
