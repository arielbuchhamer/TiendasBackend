package com.tiendas.emails;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tiendas.clientes.InvitacionCliente;
import com.tiendas.clientes.ReseteoClaveCliente;

/**
 * Encola los emails de la cuenta del cliente. Corre en la misma transacción que guardó el link (listener síncrono):
 * si esa transacción se revierte, el email tampoco queda. El envío real lo hace {@link EnvioEmails}.
 */
@Component
@EmailHabilitado
class EmailsCliente {

	private final EmailPendienteRepository repository;
	private final PlantillasCliente plantillas;
	private final EmailProperties email;

	EmailsCliente(EmailPendienteRepository repository, PlantillasCliente plantillas, EmailProperties email) {
		this.repository = repository;
		this.plantillas = plantillas;
		this.email = email;
	}

	@EventListener
	void alInvitar(InvitacionCliente evento) {
		encolar(TipoEmail.CLIENTE_INVITACION, evento.clienteId(), evento.email(),
				plantillas.invitacion(evento.nombre(), evento.link(), evento.vigencia()));
	}

	@EventListener
	void alPedirReseteo(ReseteoClaveCliente evento) {
		encolar(TipoEmail.CLIENTE_RESETEO_CLAVE, evento.clienteId(), evento.email(),
				plantillas.reseteoClave(evento.nombre(), evento.link(), evento.vigencia()));
	}

	private void encolar(TipoEmail tipo, Long clienteId, String destinatario, ContenidoEmail contenido) {
		EmailPendiente pendiente = new EmailPendiente();
		pendiente.setTipo(tipo);
		pendiente.setClienteId(clienteId);
		pendiente.setDestinatarios(destinatario);
		pendiente.setResponderA(email.responderACliente());
		pendiente.setAsunto(contenido.asunto());
		pendiente.setHtml(contenido.html());
		pendiente.setTexto(contenido.texto());
		repository.save(pendiente);
	}
}
