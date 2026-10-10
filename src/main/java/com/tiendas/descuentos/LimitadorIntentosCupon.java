package com.tiendas.descuentos;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Frena a quien prueba códigos al azar hasta adivinar uno: después de varios códigos inexistentes desde
 * la misma IP, se rechazan los intentos por un rato. En memoria alcanza porque hay una instancia por tienda.
 */
@Component
class LimitadorIntentosCupon {

	static final int MAX_FALLOS_POR_IP = 15;
	private static final Duration VENTANA = Duration.ofMinutes(15);

	private final Cache<String, AtomicInteger> fallos = Caffeine.newBuilder()
			.expireAfterWrite(VENTANA)
			.maximumSize(100_000)
			.build();

	boolean estaBloqueado(String ip) {
		AtomicInteger contador = fallos.getIfPresent(ip);
		return contador != null && contador.get() >= MAX_FALLOS_POR_IP;
	}

	void registrarFallo(String ip) {
		fallos.get(ip, clave -> new AtomicInteger()).incrementAndGet();
	}
}
