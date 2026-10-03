package com.tiendas.facturacion;

import com.tiendas.comun.ServicioExternoException;

/** Error de comunicación con AFRelay o respuesta inválida (HTTP 502). */
public class AfRelayException extends ServicioExternoException {

	public AfRelayException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
