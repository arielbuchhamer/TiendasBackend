package com.tiendas.emails;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import com.tiendas.config.TiendaProperties;
import com.tiendas.pedidos.DatosCliente;
import com.tiendas.pedidos.DireccionEnvio;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoItem;
import com.tiendas.pedidos.TipoEntrega;

/**
 * Arma los emails de un pedido en HTML y en texto plano (el texto mejora la entregabilidad y lo usan clientes de
 * correo sin HTML). Todo dato que vino del comprador se escapa: nombres o direcciones con HTML no pueden inyectar
 * contenido en el email que recibe el administrador.
 */
@Component
@EmailHabilitado
class PlantillasPedido {

	record Contenido(String asunto, String html, String texto) {
	}

	private record Total(String etiqueta, String valor, boolean destacado) {
	}

	private static final Locale LOCALE = Locale.forLanguageTag("es-AR");

	private final TiendaProperties tienda;

	PlantillasPedido(TiendaProperties tienda) {
		this.tienda = tienda;
	}

	Contenido confirmacionCliente(Pedido pedido) {
		DatosCliente cliente = pedido.getCliente();
		String asunto = "Confirmación de tu compra en " + tienda.nombre();
		List<String> intro = List.of(
				"¡Gracias por tu compra, " + cliente.getNombre() + "!",
				"Recibimos el pago de tu pedido. Este es el detalle:");
		List<String> cierre = List.of(
				"Código de pedido: " + pedido.getCodigo(),
				"Si tenés alguna consulta, respondé este email.");
		return armar(asunto, pedido, intro, List.of(), cierre);
	}

	Contenido avisoAdmin(Pedido pedido) {
		DatosCliente cliente = pedido.getCliente();
		String asunto = "Nueva venta #" + pedido.getId() + " · " + importe(pedido.getTotal(), pedido.getMoneda());
		List<String> intro = List.of("Se aprobó el pago del pedido #" + pedido.getId() + ".");
		List<String> datosCliente = new ArrayList<>(List.of(
				"Cliente: " + cliente.getNombre() + " " + cliente.getApellido(),
				"Documento: " + cliente.getDocumento(),
				"Email: " + cliente.getEmail()));
		if (StringUtils.hasText(cliente.getTelefono())) {
			datosCliente.add("Teléfono: " + cliente.getTelefono());
		}
		List<String> cierre = List.of(
				"Código de pedido: " + pedido.getCodigo(),
				"Respondé este email para escribirle al cliente.");
		return armar(asunto, pedido, intro, datosCliente, cierre);
	}

	private Contenido armar(String asunto, Pedido pedido, List<String> intro, List<String> datosCliente,
			List<String> cierre) {
		String moneda = pedido.getMoneda();
		String entrega = entrega(pedido);
		List<Total> totales = totales(pedido);

		StringBuilder html = new StringBuilder()
				.append("<!doctype html><html><body style=\"margin:0;padding:24px;background:#f4f4f5;")
				.append("font-family:Arial,Helvetica,sans-serif;color:#18181b\">")
				.append("<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:8px;padding:24px\">")
				.append("<h2 style=\"margin:0 0 16px\">").append(esc(tienda.nombre())).append("</h2>");
		intro.forEach(linea -> html.append(parrafo(linea)));
		datosCliente.forEach(linea -> html.append(parrafo(linea)));

		html.append("<table style=\"width:100%;border-collapse:collapse;margin:16px 0;font-size:14px\">");
		for (PedidoItem item : pedido.getItems()) {
			html.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid #e4e4e7\">")
					.append(esc(descripcion(item))).append("<br><span style=\"color:#71717a\">")
					.append(esc(cantidad(item))).append(" × ").append(esc(importe(item.getPrecioUnitario(), moneda)))
					.append("</span></td><td style=\"padding:8px 0;border-bottom:1px solid #e4e4e7;text-align:right;")
					.append("white-space:nowrap\">").append(esc(importe(item.getSubtotal(), moneda))).append("</td></tr>");
		}
		totales.forEach(total -> html.append(filaTotal(total)));
		html.append("</table>").append(parrafo(entrega));
		cierre.forEach(linea -> html.append(parrafo(linea)));
		html.append("<p style=\"margin:24px 0 0;font-size:12px;color:#71717a\"><a href=\"")
				.append(esc(tienda.frontendUrl())).append("\" style=\"color:#71717a\">").append(esc(tienda.frontendUrl()))
				.append("</a></p></div></body></html>");

		StringBuilder texto = new StringBuilder();
		intro.forEach(linea -> texto.append(linea).append("\n"));
		datosCliente.forEach(linea -> texto.append(linea).append("\n"));
		texto.append("\n");
		for (PedidoItem item : pedido.getItems()) {
			texto.append("- ").append(descripcion(item)).append(": ").append(cantidad(item)).append(" × ")
					.append(importe(item.getPrecioUnitario(), moneda)).append(" = ")
					.append(importe(item.getSubtotal(), moneda)).append("\n");
		}
		texto.append("\n");
		totales.forEach(total -> texto.append(total.etiqueta()).append(": ").append(total.valor()).append("\n"));
		texto.append("\n").append(entrega).append("\n\n");
		cierre.forEach(linea -> texto.append(linea).append("\n"));
		texto.append("\n").append(tienda.nombre()).append(" · ").append(tienda.frontendUrl()).append("\n");

		return new Contenido(asunto, html.toString(), texto.toString());
	}

	private static List<Total> totales(Pedido pedido) {
		String moneda = pedido.getMoneda();
		List<Total> totales = new ArrayList<>();
		totales.add(new Total("Subtotal", importe(pedido.getSubtotal(), moneda), false));
		if (pedido.getEntrega() != TipoEntrega.RETIRO) {
			totales.add(new Total("Envío", pedido.isEnvioAConvenir() ? "A convenir"
					: importe(pedido.getCostoEnvio(), moneda), false));
		}
		if (StringUtils.hasText(pedido.getCuponCodigo())) {
			// Un cupón que no descuenta nada es de envío gratis en una tienda que no cobra el envío en la web
			boolean conDescuento = pedido.getDescuento() != null && pedido.getDescuento().signum() > 0;
			totales.add(new Total("Cupón " + pedido.getCuponCodigo(),
					conDescuento ? "- " + importe(pedido.getDescuento(), moneda) : "Envío gratis", false));
		}
		totales.add(new Total("Total", importe(pedido.getTotal(), moneda), true));
		return totales;
	}

	private static String entrega(Pedido pedido) {
		String descripcion = StringUtils.hasText(pedido.getEnvioDescripcion()) ? pedido.getEnvioDescripcion() : null;
		return switch (pedido.getEntrega()) {
			case RETIRO -> "Entrega: " + (descripcion != null ? descripcion : "retiro en el local") + ".";
			case SUCURSAL -> "Entrega: " + (descripcion != null ? descripcion : "retiro en sucursal de Correo Argentino")
					+ (StringUtils.hasText(pedido.getEnvioSucursalDetalle()) ? ". Sucursal: " + pedido.getEnvioSucursalDetalle() : "")
					+ ".";
			case DOMICILIO -> "Entrega: " + (descripcion != null ? descripcion : "envío a domicilio")
					+ (pedido.getDireccion() != null ? ". Dirección: " + direccion(pedido.getDireccion()) : "") + ".";
		};
	}

	private static String direccion(DireccionEnvio d) {
		StringBuilder direccion = new StringBuilder(d.getCalle()).append(" ").append(d.getNumero());
		if (StringUtils.hasText(d.getPiso())) {
			direccion.append(", piso ").append(d.getPiso());
		}
		if (StringUtils.hasText(d.getDepartamento())) {
			direccion.append(", depto. ").append(d.getDepartamento());
		}
		return direccion.append(", ").append(d.getCiudad()).append(", ").append(d.getProvincia())
				.append(" (CP ").append(d.getCodigoPostal()).append(")").toString();
	}

	private static String descripcion(PedidoItem item) {
		return StringUtils.hasText(item.getVarianteDescripcion())
				? item.getProductoNombre() + " (" + item.getVarianteDescripcion() + ")"
				: item.getProductoNombre();
	}

	private static String cantidad(PedidoItem item) {
		NumberFormat formato = NumberFormat.getNumberInstance(LOCALE);
		formato.setMaximumFractionDigits(3);
		return formato.format(item.getCantidad()) + " " + item.getUnidadVenta().simbolo();
	}

	private static String importe(BigDecimal monto, String moneda) {
		NumberFormat formato = NumberFormat.getNumberInstance(LOCALE);
		formato.setMinimumFractionDigits(2);
		formato.setMaximumFractionDigits(2);
		return Currency.getInstance(moneda).getSymbol(LOCALE) + " " + formato.format(monto);
	}

	private static String filaTotal(Total total) {
		String peso = total.destacado() ? "font-weight:bold;font-size:16px;" : "";
		return "<tr><td style=\"padding:6px 0;" + peso + "\">" + esc(total.etiqueta()) + "</td><td style=\"padding:6px 0;"
				+ "text-align:right;white-space:nowrap;" + peso + "\">" + esc(total.valor()) + "</td></tr>";
	}

	private static String parrafo(String texto) {
		return "<p style=\"margin:0 0 8px;font-size:14px\">" + esc(texto) + "</p>";
	}

	private static String esc(String texto) {
		return HtmlUtils.htmlEscape(texto, "UTF-8");
	}
}
