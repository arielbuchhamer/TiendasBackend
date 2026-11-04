package com.tiendas.descuentos;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.tiendas.comun.ContadorIntentos;

/**
 * Frena a quien prueba códigos al azar hasta adivinar uno: después de varios códigos inexistentes desde
 * la misma IP, se rechazan los intentos por un rato.
 */
@Component
class LimitadorIntentosCupon {

	static final int MAX_FALLOS_POR_IP = 15;
	private static final Duration VENTANA = Duration.ofMinutes(15);

	private final ContadorIntentos fallos = new ContadorIntentos(MAX_FALLOS_POR_IP, VENTANA);

	boolean estaBloqueado(String ip) {
		return fallos.superado(ip);
	}

	void registrarFallo(String ip) {
		fallos.registrar(ip);
	}
}
