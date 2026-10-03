package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuración fiscal de la tienda. No tiene valores por defecto de ningún emisor real:
 * si la facturación está habilitada y falta un dato obligatorio, la aplicación no arranca.
 */
@ConfigurationProperties(prefix = "facturacion")
public record FacturacionProperties(
		@DefaultValue("false") boolean habilitada,
		@DefaultValue("1") Integer puntoVenta,
		@DefaultValue("FACTURA_C") TipoComprobante tipoComprobante,
		@DefaultValue("PES") String moneda,
		@DefaultValue("1") BigDecimal cotizacion,
		@DefaultValue("IVA_21") AlicuotaIva alicuotaIvaDefault,
		/**
		 * Umbral RG 4444 (actualizado por RG 5866/2026, vigente desde 01/07/2026): desde este total, los
		 * comprobantes B/C requieren identificar al comprador (no se puede usar Consumidor Final, DocTipo 99).
		 */
		@DefaultValue("10000000") BigDecimal umbralIdentificacionConsumidorFinal,
		@DefaultValue Emisor emisor,
		@DefaultValue Pdf pdf,
		@DefaultValue AfRelay afrelay) {

	public FacturacionProperties {
		if (habilitada) {
			requerir(emisor.cuit(), "facturacion.emisor.cuit");
			requerir(emisor.razonSocial(), "facturacion.emisor.razon-social");
			requerir(emisor.condicionIva() == null ? null : emisor.condicionIva().name(), "facturacion.emisor.condicion-iva");
			requerir(afrelay.url(), "facturacion.afrelay.url");
			requerir(afrelay.token(), "facturacion.afrelay.token");
		}
	}

	public record Emisor(String cuit, String razonSocial, CondicionIva condicionIva, String domicilio,
			String ingresosBrutos, LocalDate inicioActividades) {
	}

	/**
	 * @param logo        recurso de Spring (ej. {@code https://...}, {@code file:/app/logo.png}); opcional
	 * @param colorAcento color de marca en hexadecimal
	 */
	public record Pdf(String logo, @DefaultValue("#4F46E5") String colorAcento) {
	}

	/** Servicio AFRelay que firma y envía los requests a ARCA. */
	public record AfRelay(String url, String token) {
	}

	private static void requerir(String valor, String propiedad) {
		if (!StringUtils.hasText(valor)) {
			throw new IllegalStateException("Facturación habilitada: falta configurar " + propiedad);
		}
	}
}
