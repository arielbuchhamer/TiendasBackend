package com.tiendas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Las páginas ({@code Page<T>}) se serializan con un formato JSON estable:
 * {@code { "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }}.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
