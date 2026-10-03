package com.tiendas.facturacion;

public enum CondicionIva {
	RESPONSABLE_INSCRIPTO(1),
	MONOTRIBUTISTA(6),
	CONSUMIDOR_FINAL(5),
	EXENTO(4),
	NO_RESPONSABLE(15);

	/** Código de CondicionIVAReceptorId (RG 5616). */
	private final int codigoArca;

	CondicionIva(int codigoArca) {
		this.codigoArca = codigoArca;
	}

	public int getCodigoArca() {
		return codigoArca;
	}
}
