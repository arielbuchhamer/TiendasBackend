package com.tiendas.archivos;

import java.util.Optional;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Almacenamiento S3-compatible (Cloudflare R2, AWS S3, MinIO, Railway Buckets). */
class AlmacenamientoS3 implements Almacenamiento {

	private final S3Client s3;
	private final String bucket;

	AlmacenamientoS3(S3Client s3, String bucket) {
		this.s3 = s3;
		this.bucket = bucket;
	}

	@Override
	public void guardar(String clave, byte[] contenido, String contentType) {
		s3.putObject(b -> b.bucket(bucket).key(clave).contentType(contentType), RequestBody.fromBytes(contenido));
	}

	@Override
	public Optional<Archivo> leer(String clave) {
		try {
			ResponseBytes<GetObjectResponse> respuesta = s3.getObjectAsBytes(b -> b.bucket(bucket).key(clave));
			return Optional.of(new Archivo(respuesta.asByteArray(), respuesta.response().contentType()));
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		}
	}

	@Override
	public void eliminar(String clave) {
		s3.deleteObject(b -> b.bucket(bucket).key(clave));
	}
}
