package com.tiendas.personalizacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Módulo opcional de personalización de productos (ej. grabado): el cliente la elige por producto y se suma un
 * precio único por unidad, que el dueño edita en el panel.
 */
@ConfigurationProperties(prefix = "personalizacion")
public record PersonalizacionProperties(@DefaultValue("false") boolean habilitada) {
}
