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
		@NotBlank @DefaultValue("/exito") String rutaExito,
		@NotBlank @DefaultValue("/pendiente") String rutaPendiente,
		@NotBlank @DefaultValue("/fallo") String rutaError,
		/**
		 * Solo desarrollo: aprueba los pedidos sin pasar por Mercado Pago. Requiere el perfil "local";
		 * en cualquier otro perfil la aplicación no arranca si está activo (no hay pasarela disponible).
		 */
		@DefaultValue("false") boolean simulado) {
}
