package com.tiendas.clientes;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.tiendas.comun.ContadorIntentos;
import com.tiendas.comun.DemasiadosIntentosException;

/**
 * Límites de los formularios públicos de clientes. Son contadores propios, separados de los del login de
 * administradores: un ataque a cuentas de clientes no bloquea al administrador, ni siquiera desde la misma IP.
 */
@Component
@ClientesHabilitado
class LimitadorIntentosCliente {

	private static final Duration VENTANA = Duration.ofMinutes(15);

	private final ContadorIntentos loginPorEmail = new ContadorIntentos(10, VENTANA);
	private final ContadorIntentos loginPorIp = new ContadorIntentos(30, VENTANA);
	// "Olvidé mi contraseña" manda un email por pedido: límite bajo por destinatario para que no se use para molestar
	private final ContadorIntentos reseteoPorEmail = new ContadorIntentos(3, VENTANA);
	private final ContadorIntentos reseteoPorIp = new ContadorIntentos(10, VENTANA);

	void validarLogin(String email, String ip) {
		if (loginPorEmail.superado(email) || loginPorIp.superado(ip)) {
			throw new DemasiadosIntentosException("Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.");
		}
	}

	void registrarFalloLogin(String email, String ip) {
		loginPorEmail.registrar(email);
		loginPorIp.registrar(ip);
	}

	void registrarExitoLogin(String email) {
		loginPorEmail.reiniciar(email);
	}

	/** Cuenta cada pedido de reseteo (exista o no la cuenta) y falla si se superó el límite. */
	void registrarPedidoReseteo(String email, String ip) {
		if (reseteoPorEmail.superado(email) || reseteoPorIp.superado(ip)) {
			throw new DemasiadosIntentosException("Ya pediste varios links. Esperá unos minutos y revisá tu email.");
		}
		reseteoPorEmail.registrar(email);
		reseteoPorIp.registrar(ip);
	}
}
