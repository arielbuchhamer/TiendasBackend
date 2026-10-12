package com.tiendas.notificaciones;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.tiendas.config.TiendaProperties;
import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoPagado;
import com.tiendas.pedidos.PedidoService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Manda los mails de una venta pagada: el aviso al vendedor y la confirmación al cliente.
 * <p>
 * Corre después del commit y en un hilo aparte: un servidor de mail lento o caído nunca demora ni revierte
 * el registro del pago. Si un mail falla queda en el log (la venta igual aparece en el panel).
 */
@Component
@NotificacionesHabilitadas
class CorreoVentas {

	private static final Logger log = LoggerFactory.getLogger(CorreoVentas.class);
	private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

	private record Logo(byte[] bytes, String tipo) {
	}

	private final PedidoService pedidoService;
	private final JavaMailSender mail;
	private final NotificacionesProperties config;
	private final TiendaProperties tienda;
	private final ResourceLoader recursos;
	private final TemplateEngine plantillas;
	private volatile Optional<Logo> logo;

	CorreoVentas(PedidoService pedidoService, JavaMailSender mail, NotificacionesProperties config,
			TiendaProperties tienda, ResourceLoader recursos) {
		this.pedidoService = pedidoService;
		this.mail = mail;
		this.config = config;
		this.tienda = tienda;
		this.recursos = recursos;
		this.plantillas = motorDePlantillas();
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void alPagarse(PedidoPagado evento) {
		Thread.ofVirtual().name("mail-pedido-" + evento.pedidoId()).start(() -> enviar(evento));
	}

	private void enviar(PedidoPagado evento) {
		Pedido pedido;
		try {
			pedido = pedidoService.obtener(evento.pedidoId());
		} catch (RuntimeException e) {
			log.error("No se pudo cargar el pedido {} para notificar la venta", evento.pedidoId(), e);
			return;
		}
		Map<String, Object> datos = VistaPedido.de(pedido, tienda, config);
		datos.put("requiereRevision", evento.requiereRevision());
		String numero = VistaPedido.numero(pedido);

		if (!config.avisosVendedor().isEmpty()) {
			String asunto = (evento.requiereRevision() ? "Venta para revisar" : "Nueva venta") + " #" + numero + " · "
					+ datos.get("cliente") + " · " + datos.get("total");
			mandar(config.avisosVendedor(), pedido.getCliente().getEmail(), asunto, "venta-vendedor", datos, numero);
		}
		// Un pedido en revisión (monto distinto o sin stock) no se confirma al cliente hasta que el vendedor lo resuelva
		if (config.mailCliente() && !evento.requiereRevision()) {
			String asunto = "¡Gracias por tu compra en " + tienda.nombre() + "! Pedido #" + numero;
			mandar(List.of(pedido.getCliente().getEmail()), null, asunto, "venta-cliente", datos, numero);
		}
	}

	private void mandar(List<String> para, String responderA, String asunto, String plantilla, Map<String, Object> datos,
			String numero) {
		try {
			Optional<Logo> logoActual = logo();
			Context contexto = new Context(ES_AR, datos);
			contexto.setVariable("conLogo", logoActual.isPresent());
			String html = plantillas.process(plantilla, contexto);

			MimeMessage mensaje = mail.createMimeMessage();
			MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, "UTF-8");
			ayudante.setFrom(config.remitente());
			ayudante.setTo(para.toArray(String[]::new));
			if (StringUtils.hasText(responderA)) {
				ayudante.setReplyTo(responderA);
			}
			ayudante.setSubject(asunto);
			ayudante.setText(textoPlano(datos), html);
			if (logoActual.isPresent()) {
				ayudante.addInline("logo", new ByteArrayResource(logoActual.get().bytes()), logoActual.get().tipo());
			}
			mail.send(mensaje);
			log.info("Mail '{}' del pedido {} enviado a {}", plantilla, numero, para);
		} catch (MessagingException | RuntimeException e) {
			log.error("No se pudo mandar el mail '{}' del pedido {}", plantilla, numero, e);
		}
	}

	// Versión sin formato, para clientes de correo que no muestran HTML
	private static String textoPlano(Map<String, Object> d) {
		StringBuilder texto = new StringBuilder();
		texto.append(d.get("tienda")).append(" · Pedido #").append(d.get("numero")).append("\n\n");
		for (Object o : (List<?>) d.get("items")) {
			Map<?, ?> item = (Map<?, ?>) o;
			texto.append("- ").append(item.get("cantidad")).append(" x ").append(item.get("nombre"));
			if (item.get("variante") != null) {
				texto.append(" (").append(item.get("variante")).append(")");
			}
			texto.append(": ").append(item.get("subtotal")).append("\n");
		}
		texto.append("\nEnvío: ").append(d.get("envio"))
				.append("\nTotal: ").append(d.get("total"))
				.append("\nEntrega: ").append(d.get("entrega"));
		if (d.get("direccion") != null) {
			texto.append("\n").append(d.get("direccion")).append(", ").append(d.get("localidad"));
		}
		texto.append("\n\n").append(d.get("proximosPasos")).append("\n");
		return texto.toString();
	}

	/** El logo se lee una sola vez; si no se puede leer, los mails salen sin logo. */
	private Optional<Logo> logo() {
		Optional<Logo> actual = logo;
		if (actual == null) {
			actual = leerLogo();
			logo = actual;
		}
		return actual;
	}

	private Optional<Logo> leerLogo() {
		if (!StringUtils.hasText(config.logo())) {
			return Optional.empty();
		}
		Resource recurso = recursos.getResource(config.logo());
		try (InputStream entrada = recurso.getInputStream()) {
			String nombre = config.logo().toLowerCase(Locale.ROOT);
			String tipo = nombre.endsWith(".jpg") || nombre.endsWith(".jpeg") ? "image/jpeg" : "image/png";
			return Optional.of(new Logo(entrada.readAllBytes(), tipo));
		} catch (IOException e) {
			log.warn("No se pudo leer el logo de los mails ({}): se mandan sin logo", config.logo(), e);
			return Optional.empty();
		}
	}

	private static TemplateEngine motorDePlantillas() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("mails/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(true);
		TemplateEngine motor = new TemplateEngine();
		motor.setTemplateResolver(resolver);
		return motor;
	}
}
