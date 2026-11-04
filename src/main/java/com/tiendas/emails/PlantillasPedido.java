package com.tiendas.emails;

import static com.tiendas.emails.DisenoEmail.esc;
import static com.tiendas.emails.DisenoEmail.parrafo;
import static com.tiendas.emails.DisenoEmail.seccion;
import static com.tiendas.emails.DisenoEmail.subtitulo;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
	private static final String NO_FUE_USTED = "Si no hiciste esta compra, ignorá este email.";

	private final TiendaProperties tienda;

	PlantillasPedido(TiendaProperties tienda) {
		this.tienda = tienda;
	}

	Contenido confirmacionCliente(Pedido pedido) {
		Partes partes = new Partes(
				"¡Gracias por tu compra, " + pedido.getCliente().getNombre() + "!",
				List.of("Recibimos el pago de tu pedido en " + tienda.nombre() + ". Te dejamos el detalle:"),
				null, List.of(),
				List.of("Si tenés alguna consulta, respondé este email y te contestamos."),
				true);
		return armar("Confirmación de tu compra en " + tienda.nombre(), pedido, partes);
	}

	Contenido avisoAdmin(Pedido pedido) {
		DatosCliente cliente = pedido.getCliente();
		List<String> datosCliente = new ArrayList<>(List.of(
				"Nombre: " + cliente.getNombre() + " " + cliente.getApellido(),
				"Documento: " + cliente.getDocumento(),
				"Email: " + cliente.getEmail()));
		if (StringUtils.hasText(cliente.getTelefono())) {
			datosCliente.add("Teléfono: " + cliente.getTelefono());
		}
		Partes partes = new Partes(
				tienda.nombre(),
				List.of("Se aprobó el pago del pedido #" + pedido.getId() + "."),
				"Cliente", datosCliente,
				List.of("Respondé este email para escribirle al cliente."),
				false);
		return armar("Nueva venta #" + pedido.getId() + " · " + importe(pedido.getTotal(), pedido.getMoneda()),
				pedido, partes);
	}

	/**
	 * Lo que cambia entre un email y otro; el detalle del pedido y la entrega son iguales para los dos.
	 *
	 * @param firma cierra con "Saludos, {tienda}" y la aclaración para quien no hizo la compra (email al cliente)
	 */
	private record Partes(String titulo, List<String> intro, String tituloDatos, List<String> datos,
			List<String> cierre, boolean firma) {
	}

	private Contenido armar(String asunto, Pedido pedido, Partes partes) {
		String moneda = pedido.getMoneda();
		List<String> entrega = entrega(pedido);
		List<Total> totales = totales(pedido);

		StringBuilder html = new StringBuilder(DisenoEmail.abrir(partes.titulo()));
		partes.intro().forEach(linea -> html.append(parrafo(linea)));
		if (!partes.datos().isEmpty()) {
			html.append(seccion(partes.tituloDatos(), partes.datos()));
		}

		html.append(subtitulo("Detalle del pedido"))
				.append("<table style=\"width:100%;border-collapse:collapse;margin:0 0 8px;font-size:14px\">");
		for (PedidoItem item : pedido.getItems()) {
			html.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid #e4e4e7\">")
					.append(esc(descripcion(item))).append("<br><span style=\"color:#71717a\">")
					.append(esc(cantidad(item))).append(" × ").append(esc(importe(item.getPrecioUnitario(), moneda)))
					.append("</span></td><td style=\"padding:8px 0;border-bottom:1px solid #e4e4e7;text-align:right;")
					.append("white-space:nowrap\">").append(esc(importe(item.getSubtotal(), moneda))).append("</td></tr>");
		}
		totales.forEach(total -> html.append(filaTotal(total)));
		html.append("</table>")
				.append(seccion("Entrega", entrega))
				.append("<div style=\"margin-top:20px\">");
		partes.cierre().forEach(linea -> html.append(parrafo(linea)));
		if (partes.firma()) {
			html.append(DisenoEmail.firma(tienda.nombre()));
		}
		List<String> pie = new ArrayList<>(List.of("Código de pedido: " + pedido.getCodigo()));
		if (partes.firma()) {
			pie.add(NO_FUE_USTED);
		}
		html.append("</div>").append(DisenoEmail.cerrar(tienda.frontendUrl(), pie));

		StringBuilder texto = new StringBuilder(partes.titulo()).append("\n\n");
		partes.intro().forEach(linea -> texto.append(linea).append("\n"));
		if (!partes.datos().isEmpty()) {
			texto.append("\n").append(partes.tituloDatos()).append("\n");
			partes.datos().forEach(linea -> texto.append(linea).append("\n"));
		}
		texto.append("\nDetalle del pedido\n");
		for (PedidoItem item : pedido.getItems()) {
			texto.append("- ").append(descripcion(item)).append(": ").append(cantidad(item)).append(" × ")
					.append(importe(item.getPrecioUnitario(), moneda)).append(" = ")
					.append(importe(item.getSubtotal(), moneda)).append("\n");
		}
		texto.append("\n");
		totales.forEach(total -> texto.append(total.etiqueta()).append(": ").append(total.valor()).append("\n"));
		texto.append("\nEntrega\n");
		entrega.forEach(linea -> texto.append(linea).append("\n"));
		texto.append("\n");
		partes.cierre().forEach(linea -> texto.append(linea).append("\n"));
		if (partes.firma()) {
			texto.append("\nSaludos,\n").append(tienda.nombre()).append("\n");
		}
		texto.append("\n").append(tienda.frontendUrl()).append("\nCódigo de pedido: ").append(pedido.getCodigo())
				.append("\n");
		if (partes.firma()) {
			texto.append(NO_FUE_USTED).append("\n");
		}
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

	private static List<String> entrega(Pedido pedido) {
		String descripcion = StringUtils.hasText(pedido.getEnvioDescripcion()) ? pedido.getEnvioDescripcion() : null;
		List<String> lineas = new ArrayList<>();
		switch (pedido.getEntrega()) {
			case RETIRO -> lineas.add(descripcion != null ? descripcion : "Retiro en el local");
			case SUCURSAL -> {
				lineas.add(descripcion != null ? descripcion : "Retiro en sucursal de Correo Argentino");
				if (StringUtils.hasText(pedido.getEnvioSucursalDetalle())) {
					lineas.add("Sucursal: " + pedido.getEnvioSucursalDetalle());
				}
			}
			case DOMICILIO -> {
				lineas.add(descripcion != null ? descripcion : "Envío a domicilio");
				DireccionEnvio d = pedido.getDireccion();
				if (d != null) {
					lineas.add("Dirección: " + direccion(d));
					lineas.add("Ciudad: " + d.getCiudad());
					lineas.add("Provincia: " + d.getProvincia());
					lineas.add("Código postal: " + d.getCodigoPostal());
				}
			}
		}
		return lineas;
	}

	private static String direccion(DireccionEnvio d) {
		StringBuilder direccion = new StringBuilder(d.getCalle()).append(" ").append(d.getNumero());
		if (StringUtils.hasText(d.getPiso())) {
			direccion.append(", piso ").append(d.getPiso());
		}
		if (StringUtils.hasText(d.getDepartamento())) {
			direccion.append(", depto. ").append(d.getDepartamento());
		}
		return direccion.toString();
	}

	private static String descripcion(PedidoItem item) {
		return StringUtils.hasText(item.getPersonalizacionDetalle())
				? item.titulo() + ": \"" + item.getPersonalizacionDetalle() + "\""
				: item.titulo();
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
}
