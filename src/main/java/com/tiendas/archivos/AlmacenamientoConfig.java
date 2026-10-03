package com.tiendas.archivos;

import java.net.URI;
import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class AlmacenamientoConfig {

	@Bean
	Almacenamiento almacenamiento(AlmacenamientoProperties propiedades) {
		return switch (propiedades.tipo()) {
			case LOCAL -> new AlmacenamientoLocal(Path.of(propiedades.local().directorio()));
			case S3 -> new AlmacenamientoS3(clienteS3(propiedades.s3()), requerido(propiedades.s3().bucket(), "bucket"));
		};
	}

	private static S3Client clienteS3(AlmacenamientoProperties.S3 s3) {
		var builder = S3Client.builder()
				.region(Region.of(s3.region()))
				.forcePathStyle(s3.pathStyle())
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
						requerido(s3.accessKey(), "access-key"), requerido(s3.secretKey(), "secret-key"))));
		if (StringUtils.hasText(s3.endpoint())) {
			builder.endpointOverride(URI.create(s3.endpoint()));
		}
		return builder.build();
	}

	private static String requerido(String valor, String nombre) {
		if (!StringUtils.hasText(valor)) {
			throw new IllegalStateException("Falta configurar almacenamiento.s3." + nombre);
		}
		return valor;
	}
}
