package com.tiendas.emails;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tiendas.config.TiendaProperties;

/** Emails de la cuenta del cliente (invitación y cambio de contraseña), con el mismo diseño que los de pedidos. */
@Component
@EmailHabilitado
class PlantillasCliente {

	private final TiendaProperties tienda;

	PlantillasCliente(TiendaProperties tienda) {
		this.tienda = tienda;
	}

	ContenidoEmail invitacion(String nombre, String link, Duration vigencia) {
		return armar("Activá tu cuenta en " + tienda.nombre(),
				"¡Hola, " + nombre + "!",
				List.of(tienda.nombre() + " te creó una cuenta para comprar en la tienda.",
						"Para empezar, elegí tu contraseña:"),
				"Activar mi cuenta", link,
				"El link vence en " + texto(vigencia) + " y se puede usar una sola vez.",
				"Si no esperabas este email, ignoralo.");
	}

	ContenidoEmail reseteoClave(String nombre, String link, Duration vigencia) {
		return armar("Cambiá tu contraseña de " + tienda.nombre(),
				"Hola, " + nombre,
				List.of("Recibimos un pedido para cambiar la contraseña de tu cuenta.",
						"Para elegir una nueva, entrá acá:"),
				"Cambiar contraseña", link,
				"El link vence en " + texto(vigencia) + " y se puede usar una sola vez.",
				"Si no lo pediste, ignorá este email: tu contraseña sigue siendo la misma.");
	}

	private ContenidoEmail armar(String asunto, String titulo, List<String> intro, String boton,
			String link, String vencimiento, String aclaracion) {
		StringBuilder html = new StringBuilder(DisenoEmail.abrir(titulo));
		intro.forEach(linea -> html.append(DisenoEmail.parrafo(linea)));
		html.append(DisenoEmail.boton(boton, link))
				.append(DisenoEmail.parrafo(vencimiento))
				.append(DisenoEmail.firma(tienda.nombre()))
				.append(DisenoEmail.cerrar(tienda.frontendUrl(), List.of(aclaracion)));

		StringBuilder texto = new StringBuilder(titulo).append("\n\n");
		intro.forEach(linea -> texto.append(linea).append("\n"));
		texto.append("\n").append(link).append("\n\n").append(vencimiento).append("\n\nSaludos,\n")
				.append(tienda.nombre()).append("\n\n").append(tienda.frontendUrl()).append("\n").append(aclaracion)
				.append("\n");
		return new ContenidoEmail(asunto, html.toString(), texto.toString());
	}

	private static String texto(Duration vigencia) {
		long minutos = vigencia.toMinutes();
		if (minutos % 60 != 0) {
			return minutos == 1 ? "1 minuto" : minutos + " minutos";
		}
		long horas = minutos / 60;
		return horas == 1 ? "1 hora" : horas + " horas";
	}
}
