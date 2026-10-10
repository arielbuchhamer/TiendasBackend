package com.tiendas.descuentos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Módulo opcional de cupones de descuento. */
@ConfigurationProperties(prefix = "descuentos")
public record DescuentosProperties(@DefaultValue("false") boolean habilitada) {
}
