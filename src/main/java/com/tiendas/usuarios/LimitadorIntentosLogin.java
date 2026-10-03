package com.tiendas.usuarios;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Frena ataques de fuerza bruta al login: después de varios intentos fallidos se bloquea temporalmente
 * el usuario atacado y, por separado, la IP de origen. En memoria alcanza porque hay una instancia por tienda.
 */
@Component
public class LimitadorIntentosLogin {

	static final int MAX_FALLOS_POR_USUARIO = 10;
	static final int MAX_FALLOS_POR_IP = 30;
	private static final Duration VENTANA = Duration.ofMinutes(15);

	private final Cache<String, AtomicInteger> fallos = Caffeine.newBuilder()
			.expireAfterWrite(VENTANA)
			.maximumSize(100_000)
			.build();

	public boolean estaBloqueado(String username, String ip) {
		return cantidad(claveUsuario(username)) >= MAX_FALLOS_POR_USUARIO
				|| cantidad(claveIp(ip)) >= MAX_FALLOS_POR_IP;
	}

	public void registrarFallo(String username, String ip) {
		fallos.get(claveUsuario(username), clave -> new AtomicInteger()).incrementAndGet();
		fallos.get(claveIp(ip), clave -> new AtomicInteger()).incrementAndGet();
	}

	public void registrarExito(String username) {
		fallos.invalidate(claveUsuario(username));
	}

	private int cantidad(String clave) {
		AtomicInteger contador = fallos.getIfPresent(clave);
		return contador == null ? 0 : contador.get();
	}

	private static String claveUsuario(String username) {
		return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
	}

	private static String claveIp(String ip) {
		return "ip:" + ip;
	}
}
