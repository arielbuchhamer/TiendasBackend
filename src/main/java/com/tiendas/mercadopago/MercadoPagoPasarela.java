package com.tiendas.mercadopago;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.tiendas.comun.ServicioExternoException;
import com.tiendas.config.TiendaProperties;
import com.tiendas.pedidos.Pago;
import com.tiendas.pedidos.PasarelaPago;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoItem;

/**
 * Integración con Checkout Pro de Mercado Pago. El access token se pasa en cada request
 * ({@link MPRequestOptions}) en lugar de configurarlo globalmente en el SDK: no hay estado estático
 * y no depende de qué endpoint se llamó primero.
 */
@Component
@MercadoPagoReal
public class MercadoPagoPasarela implements PasarelaPago {

	private static final int MAX_DESCRIPTOR_RESUMEN = 22;

	private final MercadoPagoProperties mercadoPago;
	private final TiendaProperties tienda;
	private final MPRequestOptions opciones;

	MercadoPagoPasarela(MercadoPagoProperties mercadoPago, TiendaProperties tienda) {
		this.mercadoPago = mercadoPago;
		this.tienda = tienda;
		this.opciones = MPRequestOptions.builder()
				.accessToken(mercadoPago.accessToken())
				.connectionTimeout((int) Duration.ofSeconds(5).toMillis())
				.socketTimeout((int) Duration.ofSeconds(20).toMillis())
				.build();
	}

	@Override
	public CheckoutExterno crearCheckout(Pedido pedido) {
		PreferenceRequest preferencia = PreferenceRequest.builder()
				.externalReference(pedido.getCodigo().toString())
				.items(items(pedido))
				.payer(PreferencePayerRequest.builder()
						.name(pedido.getCliente().getNombre())
						.surname(pedido.getCliente().getApellido())
						.email(pedido.getCliente().getEmail())
						.build())
				.backUrls(PreferenceBackUrlsRequest.builder()
						.success(tienda.frontendUrl() + mercadoPago.rutaExito())
						.pending(tienda.frontendUrl() + mercadoPago.rutaPendiente())
						.failure(tienda.frontendUrl() + mercadoPago.rutaError())
						.build())
				.autoReturn("approved")
				.notificationUrl(tienda.apiUrl() + "/api/v1/webhooks/mercadopago")
				.statementDescriptor(recortar(tienda.nombre(), MAX_DESCRIPTOR_RESUMEN))
				// El link de pago vence junto con el pedido
				.expires(true)
				.expirationDateTo(OffsetDateTime.now().plusHours(tienda.pedidos().horasVencimiento()))
				.build();
		try {
			Preference creada = new PreferenceClient().create(preferencia, opciones);
			return new CheckoutExterno(creada.getId(), creada.getInitPoint());
		} catch (MPApiException e) {
			throw new ServicioExternoException("Mercado Pago rechazó la preferencia (HTTP " + e.getStatusCode() + "): "
					+ e.getApiResponse().getContent(), e);
		} catch (MPException e) {
			throw new ServicioExternoException("Error comunicando con Mercado Pago", e);
		}
	}

	/** Consulta un pago a la API de MP. Vacío si el pago no existe en la cuenta de la tienda. */
	public Optional<PagoInformado> obtenerPago(String paymentId) {
		try {
			Payment pago = new PaymentClient().get(Long.valueOf(paymentId), opciones);
			return Optional.of(new PagoInformado(
					Pago.MERCADO_PAGO,
					String.valueOf(pago.getId()),
					pago.getExternalReference(),
					resultado(pago.getStatus()),
					pago.getStatus(),
					pago.getStatusDetail(),
					pago.getTransactionAmount(),
					pago.getCurrencyId(),
					pago.getPaymentMethodId(),
					pago.getDateApproved() == null ? null : pago.getDateApproved().toInstant()));
		} catch (NumberFormatException e) {
			return Optional.empty();
		} catch (MPApiException e) {
			if (e.getStatusCode() == 404) {
				return Optional.empty();
			}
			throw new ServicioExternoException("Mercado Pago respondió HTTP " + e.getStatusCode() + " al consultar el pago " + paymentId, e);
		} catch (MPException e) {
			throw new ServicioExternoException("Error comunicando con Mercado Pago", e);
		}
	}

	// https://www.mercadopago.com.ar/developers/es/docs/checkout-api/response-handling/collection-results
	private static ResultadoPago resultado(String estado) {
		return switch (estado == null ? "" : estado) {
			case "approved" -> ResultadoPago.APROBADO;
			case "rejected", "cancelled" -> ResultadoPago.RECHAZADO;
			case "refunded", "charged_back" -> ResultadoPago.REINTEGRADO;
			default -> ResultadoPago.PENDIENTE; // pending, in_process, authorized, in_mediation
		};
	}

	private List<PreferenceItemRequest> items(Pedido pedido) {
		if (pedido.getDescuento().signum() > 0) {
			return List.of(itemConDescuento(pedido));
		}
		List<PreferenceItemRequest> items = new ArrayList<>();
		for (PedidoItem item : pedido.getItems()) {
			// MP solo acepta cantidades enteras: si la cantidad tiene decimales (ej. 2,5 m²) se envía una
			// unidad con el subtotal, aclarando la cantidad en el título
			boolean cantidadEntera = item.getCantidad().stripTrailingZeros().scale() <= 0;
			String titulo = item.getProductoNombre()
					+ (item.getVarianteDescripcion() == null ? "" : " (" + item.getVarianteDescripcion() + ")")
					+ (cantidadEntera ? "" : " x " + item.getCantidad().stripTrailingZeros().toPlainString() + " "
							+ item.getUnidadVenta().simbolo());
			items.add(PreferenceItemRequest.builder()
					.id(item.getSku())
					.title(recortar(titulo, 250))
					.pictureUrl(item.getImagenId() == null ? null
							: tienda.apiUrl() + "/api/v1/imagenes/" + item.getImagenId() + "/tarjeta")
					.quantity(cantidadEntera ? item.getCantidad().intValueExact() : 1)
					.unitPrice(cantidadEntera ? item.getPrecioUnitario() : item.getSubtotal())
					.currencyId(pedido.getMoneda())
					.build());
		}
		if (pedido.getCostoEnvio().compareTo(BigDecimal.ZERO) > 0) {
			items.add(PreferenceItemRequest.builder()
					.id("ENVIO")
					.title("Envío a domicilio")
					.quantity(1)
					.unitPrice(pedido.getCostoEnvio())
					.currencyId(pedido.getMoneda())
					.build());
		}
		return items;
	}

	/**
	 * MP no acepta ítems con precio negativo para restar el descuento: si hay cupón se cobra un único ítem
	 * por el total (productos + envío - descuento), con el detalle de la compra en la descripción.
	 */
	private PreferenceItemRequest itemConDescuento(Pedido pedido) {
		String detalle = pedido.getItems().stream()
				.map(item -> item.getCantidad().stripTrailingZeros().toPlainString() + " x " + item.getProductoNombre())
				.collect(Collectors.joining(", "));
		return PreferenceItemRequest.builder()
				.id("PEDIDO")
				.title(recortar("Compra en " + tienda.nombre() + " (cupón " + pedido.getCuponCodigo() + ")", 250))
				.description(recortar(detalle, 250))
				.pictureUrl(pedido.getItems().getFirst().getImagenId() == null ? null
						: tienda.apiUrl() + "/api/v1/imagenes/" + pedido.getItems().getFirst().getImagenId() + "/tarjeta")
				.quantity(1)
				.unitPrice(pedido.getTotal())
				.currencyId(pedido.getMoneda())
				.build();
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
