package com.tiendas.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de Mercado Pago de la tienda (panel de MP → Tus integraciones → Credenciales / Webhooks).
 * Son obligatorias: sin ellas la aplicación no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
		@NotBlank String accessToken,
		/** Clave secreta de Webhooks: valida la firma (x-signature) de cada notificación. */
		@NotBlank String webhookSecret,
		/** Rutas del frontend a las que vuelve el comprador después de pagar. */
		@NotBlank @DefaultValue("/checkout/exito") String rutaExito,
		@NotBlank @DefaultValue("/checkout/pendiente") String rutaPendiente,
		@NotBlank @DefaultValue("/checkout/error") String rutaError) {
}
