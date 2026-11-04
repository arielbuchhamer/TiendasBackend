package com.tiendas.usuarios;

import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.tiendas.comun.ContadorIntentos;

/**
 * Frena ataques de fuerza bruta al login: después de varios intentos fallidos se bloquea temporalmente
 * el usuario atacado y, por separado, la IP de origen.
 */
@Component
public class LimitadorIntentosLogin {

	static final int MAX_FALLOS_POR_USUARIO = 10;
	static final int MAX_FALLOS_POR_IP = 30;
	private static final Duration VENTANA = Duration.ofMinutes(15);

	private final ContadorIntentos fallosPorUsuario = new ContadorIntentos(MAX_FALLOS_POR_USUARIO, VENTANA);
	private final ContadorIntentos fallosPorIp = new ContadorIntentos(MAX_FALLOS_POR_IP, VENTANA);

	public boolean estaBloqueado(String username, String ip) {
		return fallosPorUsuario.superado(normalizar(username)) || fallosPorIp.superado(ip);
	}

	public void registrarFallo(String username, String ip) {
		fallosPorUsuario.registrar(normalizar(username));
		fallosPorIp.registrar(ip);
	}

	public void registrarExito(String username) {
		fallosPorUsuario.reiniciar(normalizar(username));
	}

	private static String normalizar(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}
}
