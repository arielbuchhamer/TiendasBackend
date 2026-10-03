package com.tiendas.archivos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "almacenamiento")
public record AlmacenamientoProperties(
		@NotNull @DefaultValue("LOCAL") Tipo tipo,
		@DefaultValue Local local,
		@DefaultValue S3 s3) {

	public enum Tipo {
		LOCAL, S3
	}

	public record Local(@DefaultValue("./datos/archivos") String directorio) {
	}

	/**
	 * Para Cloudflare R2: endpoint {@code https://<account-id>.r2.cloudflarestorage.com} y región {@code auto}.
	 * El bucket debe ser privado: los archivos se sirven a través de la API.
	 */
	public record S3(String endpoint, @DefaultValue("auto") String region, String bucket, String accessKey,
			String secretKey, @DefaultValue("false") boolean pathStyle) {
	}
}
