package com.tiendas.notificaciones;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/** Marca los beans del módulo de notificaciones: solo se crean si {@code notificaciones.habilitada=true}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty("notificaciones.habilitada")
@interface NotificacionesHabilitadas {
}
