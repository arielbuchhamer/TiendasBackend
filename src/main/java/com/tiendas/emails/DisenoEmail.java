package com.tiendas.emails;

import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * Piezas HTML comunes a todos los emails de la tienda (marco, títulos, párrafos, botón y pie), para que todos se
 * vean iguales. Todo texto pasa por {@link #esc}: los datos que cargó un cliente no pueden inyectar HTML.
 */
final class DisenoEmail {

	private DisenoEmail() {
	}

	/** Comienzo del email hasta el título incluido. */
	static String abrir(String titulo) {
		return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f4f4f5;"
				+ "font-family:Arial,Helvetica,sans-serif;color:#18181b\">"
				+ "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:8px;padding:28px\">"
				+ "<h1 style=\"margin:0 0 12px;font-size:22px;line-height:1.3\">" + esc(titulo) + "</h1>";
	}

	/** Pie con el link a la tienda y una línea por cada aclaración; cierra el email. */
	static String cerrar(String urlTienda, List<String> pie) {
		StringBuilder html = new StringBuilder("<p style=\"margin:24px 0 0;padding-top:16px;border-top:1px solid #e4e4e7;")
				.append("font-size:12px;color:#71717a\"><a href=\"").append(esc(urlTienda))
				.append("\" style=\"color:#71717a\">").append(esc(urlTienda)).append("</a>");
		pie.forEach(linea -> html.append("<br>").append(esc(linea)));
		return html.append("</p></div></body></html>").toString();
	}

	static String firma(String tienda) {
		return "<p style=\"margin:16px 0 0;font-size:14px\">Saludos,<br>" + esc(tienda) + "</p>";
	}

	static String subtitulo(String texto) {
		return "<h2 style=\"margin:24px 0 8px;font-size:15px\">" + esc(texto) + "</h2>";
	}

	/** Bloque con título opcional y una línea por dato (datos del cliente, entrega). */
	static String seccion(String titulo, List<String> lineas) {
		StringBuilder html = new StringBuilder(titulo == null ? "" : subtitulo(titulo))
				.append("<p style=\"margin:0 0 8px;font-size:14px;line-height:1.6\">");
		for (int i = 0; i < lineas.size(); i++) {
			html.append(i == 0 ? "" : "<br>").append(esc(lineas.get(i)));
		}
		return html.append("</p>").toString();
	}

	static String parrafo(String texto) {
		return "<p style=\"margin:0 0 8px;font-size:14px\">" + esc(texto) + "</p>";
	}

	/** Botón con link (los clientes de correo no soportan botones reales: es un link con estilo). */
	static String boton(String texto, String url) {
		return "<p style=\"margin:20px 0\"><a href=\"" + esc(url) + "\" style=\"display:inline-block;"
				+ "background:#18181b;color:#fff;text-decoration:none;padding:12px 20px;border-radius:6px;"
				+ "font-size:14px;font-weight:bold\">" + esc(texto) + "</a></p>";
	}

	static String esc(String texto) {
		return HtmlUtils.htmlEscape(texto, "UTF-8");
	}
}
