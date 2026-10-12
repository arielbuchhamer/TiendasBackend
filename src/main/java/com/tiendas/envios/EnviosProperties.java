package com.tiendas.envios;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Módulo opcional de envíos por zona. Apagado, el checkout usa la configuración fija de
 * {@code tienda.envio} (costo único para todo el país).
 */
@ConfigurationProperties(prefix = "envios")
public record EnviosProperties(@DefaultValue("false") boolean habilitada) {
}
