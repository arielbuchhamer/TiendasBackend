package com.tiendas.mercadopago;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Beans de la integración real con Mercado Pago. Se desactivan solo si {@code mercadopago.simulado=true},
 * que únicamente tiene efecto en el perfil {@code local} (ver {@code com.tiendas.desarrollo.PasarelaSimulada}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnBooleanProperty(name = "mercadopago.simulado", havingValue = false, matchIfMissing = true)
@interface MercadoPagoReal {
}
