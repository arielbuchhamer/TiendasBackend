package com.tiendas.notificaciones;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

import com.tiendas.config.TiendaProperties;
import com.tiendas.pedidos.DireccionEnvio;
import com.tiendas.pedidos.Pago;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoItem;
import com.tiendas.pedidos.TipoEntrega;

/**
 * Datos de un pedido ya formateados para las plantillas de mail (montos en pesos, fechas en hora argentina,
 * links). Son mapas y no records porque Thymeleaf standalone solo lee propiedades estilo JavaBean.
 */
final class VistaPedido {

	private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
	private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
	private static final DateTimeFormatter FECHA =
			DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm 'h'", ES_AR).withZone(ZONA);

	private VistaPedido() {
	}

	static Map<String, Object> de(Pedido pedido, TiendaProperties tienda, NotificacionesProperties config) {
		Map<String, Object> v = new LinkedHashMap<>();
		String numero = numero(pedido);
		boolean retiro = pedido.getEntrega() == TipoEntrega.RETIRO;

		v.put("tienda", tienda.nombre());
		v.put("color", config.colorAcento());
		v.put("numero", numero);
		v.put("fecha", FECHA.format(pedido.getAprobadoEn() != null ? pedido.getAprobadoEn() : Instant.now()));
		v.put("nombre", pedido.getCliente().getNombre());
		v.put("cliente", pedido.getCliente().getNombre() + " " + pedido.getCliente().getApellido());
		v.put("email", pedido.getCliente().getEmail());
		v.put("documento", pedido.getCliente().getDocumento());
		v.put("telefono", pedido.getCliente().getTelefono());
		v.put("whatsappCliente", whatsappCliente(pedido.getCliente().getTelefono(), numero));

		v.put("items", pedido.getItems().stream().map(item -> item(item, tienda)).toList());
		v.put("subtotal", pesos(pedido.getSubtotal()));
		v.put("envio", retiro || pedido.getCostoEnvio().signum() == 0
				? (pedido.isEnvioAConvenir() ? "A convenir" : "Gratis")
				: pesos(pedido.getCostoEnvio()));
		v.put("hayDescuento", pedido.getDescuento().signum() > 0);
		v.put("descuento", "−" + pesos(pedido.getDescuento()));
		v.put("cupon", pedido.getCuponCodigo());
		v.put("total", pesos(pedido.getTotal()));
		v.put("medioPago", medioPago(pedido));

		v.put("retiro", retiro);
		v.put("entrega", pedido.getEnvioDescripcion() != null ? pedido.getEnvioDescripcion()
				: retiro ? "Retiro en el local" : "Envío a domicilio");
		v.put("envioAConvenir", pedido.isEnvioAConvenir());
		v.put("sucursal", pedido.getEnvioSucursalDetalle());
		v.put("direccion", direccion(pedido.getDireccion()));
		v.put("localidad", localidad(pedido.getDireccion()));

		v.put("proximosPasos", config.proximosPasos());
		v.put("whatsappTienda", StringUtils.hasText(config.whatsapp())
				? "https://wa.me/" + config.whatsapp() + "?text=" + url("¡Hola! Hice el pedido #" + numero + ". ")
				: null);
		v.put("urlTienda", tienda.frontendUrl());
		v.put("urlPanel", tienda.frontendUrl() + "/ventas");
		return v;
	}

	/** Número corto para hablar del pedido: los primeros 8 caracteres del código público. */
	static String numero(Pedido pedido) {
		return pedido.getCodigo().toString().substring(0, 8).toUpperCase(Locale.ROOT);
	}

	static String pesos(BigDecimal monto) {
		boolean conCentavos = monto.stripTrailingZeros().scale() > 0;
		DecimalFormat formato = new DecimalFormat(conCentavos ? "#,##0.00" : "#,##0", DecimalFormatSymbols.getInstance(ES_AR));
		return "$ " + formato.format(monto);
	}

	private static Map<String, Object> item(PedidoItem item, TiendaProperties tienda) {
		Map<String, Object> i = new LinkedHashMap<>();
		boolean entera = item.getCantidad().stripTrailingZeros().scale() <= 0;
		i.put("nombre", item.getProductoNombre());
		i.put("variante", item.getVarianteDescripcion());
		i.put("cantidad", item.getCantidad().stripTrailingZeros().toPlainString()
				+ (entera ? "" : " " + item.getUnidadVenta().simbolo()));
		i.put("precio", pesos(item.getPrecioUnitario()));
		i.put("subtotal", pesos(item.getSubtotal()));
		i.put("imagen", item.getImagenId() == null ? null
				: tienda.apiUrl() + "/api/v1/imagenes/" + item.getImagenId() + "/miniatura");
		return i;
	}

	private static String direccion(DireccionEnvio d) {
		if (d == null || !StringUtils.hasText(d.getCalle())) {
			return null;
		}
		List<String> partes = new ArrayList<>();
		partes.add(d.getCalle() + " " + d.getNumero());
		if (StringUtils.hasText(d.getPiso())) {
			partes.add("Piso " + d.getPiso());
		}
		if (StringUtils.hasText(d.getDepartamento())) {
			partes.add("Depto. " + d.getDepartamento());
		}
		return String.join(", ", partes);
	}

	private static String localidad(DireccionEnvio d) {
		if (d == null || !StringUtils.hasText(d.getCiudad())) {
			return null;
		}
		return Stream.of(d.getCiudad(), d.getProvincia(), StringUtils.hasText(d.getCodigoPostal()) ? "CP " + d.getCodigoPostal() : null)
				.filter(StringUtils::hasText)
				.collect(Collectors.joining(", "));
	}

	// Mercado Pago informa el medio con un id (visa, master, account_money…): se traducen los más comunes
	private static String medioPago(Pedido pedido) {
		String medio = pedido.getPagos().stream()
				.filter(p -> "approved".equals(p.getEstado()))
				.map(Pago::getMedioPago)
				.filter(StringUtils::hasText)
				.findFirst()
				.orElse(null);
		if (medio == null) {
			return "Mercado Pago";
		}
		return switch (medio) {
			case "account_money" -> "Dinero en cuenta de Mercado Pago";
			case "visa", "debvisa" -> "Visa";
			case "master", "debmaster" -> "Mastercard";
			case "amex" -> "American Express";
			case "naranja" -> "Naranja";
			case "cabal", "debcabal" -> "Cabal";
			case "maestro" -> "Maestro";
			case "rapipago" -> "Rapipago";
			case "pagofacil" -> "Pago Fácil";
			default -> "Mercado Pago";
		};
	}

	/**
	 * Link de WhatsApp al cliente. El teléfono es texto libre: se arma el formato internacional de celulares
	 * argentinos (54 9 + área + número) cuando no viene con código de país. Puede fallar con números raros;
	 * por eso en el mail también se muestra el teléfono tal cual.
	 */
	private static String whatsappCliente(String telefono, String numero) {
		if (!StringUtils.hasText(telefono)) {
			return null;
		}
		String digitos = telefono.replaceAll("\\D", "");
		if (digitos.length() < 8) {
			return null;
		}
		if (!digitos.startsWith("54")) {
			digitos = "549" + sinQuince(digitos.replaceFirst("^0", ""));
		}
		return "https://wa.me/" + digitos + "?text=" + url("¡Hola! Te escribimos por tu pedido #" + numero + ". ");
	}

	/**
	 * Saca el "15" de los celulares escritos a la antigua (ej. "3442 15 123456"): característica de 2 a 4 dígitos
	 * + 15 + número da 12 dígitos; sin el 15 quedan los 10 del número nacional que usa WhatsApp.
	 */
	private static String sinQuince(String digitos) {
		if (digitos.length() != 12) {
			return digitos;
		}
		for (int corte : new int[] { 4, 3, 2 }) {
			if (digitos.startsWith("15", corte)) {
				return digitos.substring(0, corte) + digitos.substring(corte + 2);
			}
		}
		return digitos;
	}

	private static String url(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
