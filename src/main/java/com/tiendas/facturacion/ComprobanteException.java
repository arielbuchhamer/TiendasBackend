package com.tiendas.facturacion;

import com.tiendas.comun.ReglaNegocioException;

/** Solicitud fiscal inválida o comprobante rechazado por ARCA (HTTP 422). */
public class ComprobanteException extends ReglaNegocioException {

	public ComprobanteException(String mensaje) {
		super(mensaje);
	}
}
