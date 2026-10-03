package com.tiendas.mercadopago;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Valida el header {@code x-signature} de las notificaciones de Mercado Pago
 * (formato {@code ts=<timestamp>,v1=<hmac>}), según la documentación oficial:
 * HMAC-SHA256 con la clave secreta sobre {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}.
 */
@Component
class FirmaWebhook {

	private final byte[] clave;

	FirmaWebhook(MercadoPagoProperties propiedades) {
		this.clave = propiedades.webhookSecret().getBytes(StandardCharsets.UTF_8);
	}

	boolean esValida(String xSignature, String xRequestId, String dataId) {
		if (!StringUtils.hasText(xSignature) || !StringUtils.hasText(dataId)) {
			return false;
		}
		String ts = null;
		String v1 = null;
		for (String parte : xSignature.split(",")) {
			String[] claveValor = parte.trim().split("=", 2);
			if (claveValor.length == 2 && claveValor[0].equals("ts")) {
				ts = claveValor[1].trim();
			} else if (claveValor.length == 2 && claveValor[0].equals("v1")) {
				v1 = claveValor[1].trim();
			}
		}
		if (ts == null || v1 == null) {
			return false;
		}

		StringBuilder manifiesto = new StringBuilder("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
		if (StringUtils.hasText(xRequestId)) {
			manifiesto.append("request-id:").append(xRequestId).append(';');
		}
		manifiesto.append("ts:").append(ts).append(';');

		byte[] esperada = HexFormat.of().formatHex(hmac(manifiesto.toString())).getBytes(StandardCharsets.UTF_8);
		// Comparación en tiempo constante: no filtra información por diferencias de tiempo de respuesta
		return MessageDigest.isEqual(esperada, v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
	}

	private byte[] hmac(String mensaje) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(clave, "HmacSHA256"));
			return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("No se pudo calcular el HMAC", e);
		}
	}
}
