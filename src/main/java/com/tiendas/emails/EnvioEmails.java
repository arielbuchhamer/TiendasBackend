package com.tiendas.emails;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía los emails de la cola. Lee y actualiza la cola en transacciones cortas y llama a Resend fuera de ellas
 * (no retiene conexiones de la base esperando una respuesta HTTP). Una sola instancia por tienda y una tarea con
 * fixedDelay que no se superpone consigo misma: un email no lo toman dos envíos a la vez.
 * <p>
 * Reintentos con espera creciente (1, 2, 4… minutos, tope 2 h). Tras {@value #MAX_INTENTOS} intentos (unas 6 h)
 * queda FALLIDO: da tiempo a corregir una API key o un dominio sin verificar sin perder los emails del medio.
 */
@Component
@EmailHabilitado
class EnvioEmails {

	private static final Logger log = LoggerFactory.getLogger(EnvioEmails.class);

	private static final int LOTE = 20;
	private static final int MAX_INTENTOS = 10;
	private static final Duration ESPERA_MAXIMA = Duration.ofHours(2);
	private static final Duration RETENCION = Duration.ofDays(90);

	private final EmailPendienteRepository repository;
	private final ResendClient resend;
	private final TransactionTemplate transaccion;

	EnvioEmails(EmailPendienteRepository repository, ResendClient resend, TransactionTemplate transaccion) {
		this.repository = repository;
		this.resend = resend;
		this.transaccion = transaccion;
	}

	@Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT20S")
	public void enviarPendientes() {
		List<EmailPendiente> lote = transaccion.execute(
				estado -> repository.buscarParaEnviar(Instant.now(), Limit.of(LOTE)));
		for (EmailPendiente email : lote) {
			try {
				String idExterno = resend.enviar(email);
				actualizar(email.getId(), e -> {
					e.setEstado(EstadoEmail.ENVIADO);
					e.setIdExterno(idExterno);
					e.setEnviadoEn(Instant.now());
					e.setIntentos(e.getIntentos() + 1);
					e.setUltimoError(null);
				});
				log.info("Email {} ({}) enviado", email.getId(), email.getTipo());
			} catch (RuntimeException error) {
				registrarFallo(email, error);
			}
		}
	}

	/** La cola guarda datos personales del cliente: no se conservan más de lo necesario. */
	@Scheduled(cron = "0 30 4 * * *")
	public void borrarAntiguos() {
		Integer borrados = transaccion.execute(
				estado -> repository.borrarProcesadosAnterioresA(Instant.now().minus(RETENCION)));
		if (borrados != null && borrados > 0) {
			log.info("{} emails procesados de más de {} días borrados de la cola", borrados, RETENCION.toDays());
		}
	}

	private void registrarFallo(EmailPendiente email, RuntimeException error) {
		int intentos = email.getIntentos() + 1;
		String mensaje = recortar(error.getMessage(), 500);
		actualizar(email.getId(), e -> {
			e.setIntentos(intentos);
			e.setUltimoError(mensaje);
			if (intentos >= MAX_INTENTOS) {
				e.setEstado(EstadoEmail.FALLIDO);
			} else {
				e.setProximoIntento(Instant.now().plus(espera(intentos)));
			}
		});
		if (intentos >= MAX_INTENTOS) {
			log.error("Email {} ({}) descartado tras {} intentos: {}", email.getId(), email.getTipo(), intentos, mensaje);
		} else {
			log.warn("Email {} ({}) falló (intento {}), se reintenta: {}", email.getId(), email.getTipo(), intentos, mensaje);
		}
	}

	private void actualizar(Long id, Consumer<EmailPendiente> cambio) {
		transaccion.executeWithoutResult(estado -> repository.findById(id).ifPresent(cambio));
	}

	private static Duration espera(int intentos) {
		Duration espera = Duration.ofMinutes(1L << Math.min(intentos - 1, 10));
		return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
	}

	private static String recortar(String texto, int maximo) {
		if (texto == null) {
			return null;
		}
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
