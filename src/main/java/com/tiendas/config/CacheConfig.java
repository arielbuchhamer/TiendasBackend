package com.tiendas.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caché en memoria de lecturas del catálogo. Como cada tienda corre en su propia instancia, una caché
 * local es coherente sin necesidad de Redis.
 * <p>
 * El proxy transaccional hace que los {@code @CacheEvict} se apliquen recién cuando la transacción
 * confirma: así nunca se vuelve a cachear un dato que después se revierte.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/** Rubros, categorías y productos. Se invalida completa ante cualquier cambio del catálogo o del stock. */
	public static final String CATALOGO = "catalogo";

	@Bean
	CacheManager cacheManager() {
		CaffeineCacheManager caffeine = new CaffeineCacheManager(CATALOGO);
		caffeine.setCaffeine(Caffeine.newBuilder()
				.maximumSize(5_000)
				.expireAfterWrite(Duration.ofMinutes(10)));
		return new TransactionAwareCacheManagerProxy(caffeine);
	}
}
