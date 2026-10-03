package com.tiendas.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Configuración propia de cada tienda (una instancia = una tienda). Se valida al arrancar:
 * si falta un dato obligatorio la aplicación no levanta, en lugar de fallar en medio de una venta.
 */
@Validated
@ConfigurationProperties(prefix = "tienda")
public record TiendaProperties(
		@NotBlank String nombre,
		/** URL pública del frontend, sin barra final. Se usa para las URLs de retorno de Mercado Pago. */
		@NotBlank @Pattern(regexp = "https?://.+[^/]") String frontendUrl,
		/** URL pública de esta API, sin barra final. Se usa para el webhook y las imágenes de Mercado Pago. */
		@NotBlank @Pattern(regexp = "https?://.+[^/]") String apiUrl,
		@NotBlank @Pattern(regexp = "[A-Z]{3}") @DefaultValue("ARS") String moneda,
		/** Orígenes permitidos por CORS (el frontend de la tienda). */
		@NotEmpty List<String> corsOrigenes,
		@Valid @NotNull @DefaultValue Envio envio,
		@Valid @NotNull @DefaultValue Pedidos pedidos,
		@Valid @NotNull @DefaultValue Cookies cookies,
		@DefaultValue AdminInicial adminInicial) {

	public record Envio(
			@DefaultValue("true") boolean domicilioHabilitado,
			@DefaultValue("true") boolean retiroHabilitado,
			@NotNull @PositiveOrZero @DefaultValue("0") BigDecimal costoFijo) {
	}

	public record Pedidos(
			/** Horas que un pedido puede quedar pendiente de pago antes de pasar a VENCIDO. */
			@Min(1) @DefaultValue("48") int horasVencimiento) {
	}

	public record Cookies(
			/** "lax" si front y API comparten dominio (recomendado); "none" solo si están en dominios distintos. */
			@NotBlank @DefaultValue("lax") String sameSite,
			@DefaultValue("true") boolean secure,
			/**
			 * Dominio de la cookie XSRF-TOKEN, para que el frontend pueda leerla cuando la API está en un
			 * subdominio (ej. "mitienda.com.ar" con la API en api.mitienda.com.ar). Vacío = dominio de la API.
			 */
			String dominio) {
	}

	/** Usuario administrador que se crea al arrancar si la base no tiene ningún usuario. */
	public record AdminInicial(String usuario, String clave) {
	}
}
