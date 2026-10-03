package com.tiendas.comun;

/** Falló un servicio externo (Mercado Pago, AFRelay, almacenamiento) (HTTP 502). */
public class ServicioExternoException extends RuntimeException {

	public ServicioExternoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

	public ServicioExternoException(String mensaje) {
		super(mensaje);
	}
}
