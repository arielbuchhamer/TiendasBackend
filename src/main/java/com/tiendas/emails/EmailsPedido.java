package com.tiendas.emails;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tiendas.pedidos.Pedido;
import com.tiendas.pedidos.PedidoAprobado;

/**
 * Encola los emails de un pedido aprobado. Corre en la misma transacción que la aprobación (listener síncrono):
 * los emails quedan guardados si y solo si el pedido quedó aprobado. El envío real lo hace {@link EnvioEmails}.
 */
@Component
@EmailHabilitado
class EmailsPedido {

	private final EmailPendienteRepository repository;
	private final PlantillasPedido plantillas;
	private final EmailProperties email;

	EmailsPedido(EmailPendienteRepository repository, PlantillasPedido plantillas, EmailProperties email) {
		this.repository = repository;
		this.plantillas = plantillas;
		this.email = email;
	}

	@EventListener
	void alAprobarse(PedidoAprobado evento) {
		Pedido pedido = evento.pedido();
		repository.save(nuevo(TipoEmail.PEDIDO_APROBADO_CLIENTE, pedido, List.of(pedido.getCliente().getEmail()),
				email.responderACliente(), plantillas.confirmacionCliente(pedido)));
		// Responder al aviso le escribe directo al cliente
		repository.save(nuevo(TipoEmail.PEDIDO_APROBADO_ADMIN, pedido, email.admin(),
				pedido.getCliente().getEmail(), plantillas.avisoAdmin(pedido)));
	}

	private static EmailPendiente nuevo(TipoEmail tipo, Pedido pedido, List<String> destinatarios, String responderA,
			PlantillasPedido.Contenido contenido) {
		EmailPendiente pendiente = new EmailPendiente();
		pendiente.setTipo(tipo);
		pendiente.setPedidoId(pedido.getId());
		pendiente.setDestinatarios(String.join(",", destinatarios));
		pendiente.setResponderA(responderA);
		pendiente.setAsunto(contenido.asunto());
		pendiente.setHtml(contenido.html());
		pendiente.setTexto(contenido.texto());
		return pendiente;
	}
}
