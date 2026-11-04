package com.tiendas.comun;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Cuenta intentos por clave (usuario, IP, email...) dentro de una ventana de tiempo, para frenar fuerza bruta.
 * Cada contador vence a los {@code ventana} de su primer intento. En memoria alcanza porque hay una instancia por
 * tienda; el tamaño máximo evita que un atacante llene la memoria con claves distintas.
 */
public final class ContadorIntentos {

	private static final int MAXIMO_CLAVES = 100_000;

	private final int maximo;
	private final Cache<String, AtomicInteger> intentos;

	public ContadorIntentos(int maximo, Duration ventana) {
		this.maximo = maximo;
		this.intentos = Caffeine.newBuilder()
				.expireAfterWrite(ventana)
				.maximumSize(MAXIMO_CLAVES)
				.build();
	}

	/** {@code true} si la clave ya llegó al máximo de intentos de la ventana. */
	public boolean superado(String clave) {
		AtomicInteger contador = intentos.getIfPresent(clave);
		return contador != null && contador.get() >= maximo;
	}

	public void registrar(String clave) {
		intentos.get(clave, k -> new AtomicInteger()).incrementAndGet();
	}

	public void reiniciar(String clave) {
		intentos.invalidate(clave);
	}
}
