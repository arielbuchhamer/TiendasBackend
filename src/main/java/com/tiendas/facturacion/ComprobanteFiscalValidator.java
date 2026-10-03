package com.tiendas.facturacion;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
@FacturacionHabilitada
class ComprobanteFiscalValidator {

	private final FacturacionProperties facturacion;

	ComprobanteFiscalValidator(FacturacionProperties facturacion) {
		this.facturacion = facturacion;
	}

	void validar(ComprobanteSolicitud solicitud) {
		if (solicitud == null) {
			throw new ComprobanteException("La solicitud no puede ser nula");
		}
		if (isBlank(solicitud.getIdempotencyKey())) {
			throw new ComprobanteException("idempotencyKey es obligatorio");
		}
		if (solicitud.getEmisor() == null || isBlank(solicitud.getEmisor().getCuit())) {
			throw new ComprobanteException("emisor.cuit es obligatorio");
		}
		if (solicitud.getCliente() == null) {
			throw new ComprobanteException("cliente es obligatorio");
		}
		if (solicitud.getTipoComprobante() == null) {
			throw new ComprobanteException("tipoComprobante es obligatorio");
		}
		if (solicitud.getPuntoVenta() == null || solicitud.getPuntoVenta() <= 0) {
			throw new ComprobanteException("puntoVenta debe ser mayor a cero");
		}
		if (solicitud.getFecha() == null) {
			throw new ComprobanteException("fecha es obligatoria");
		}
		validarItems(solicitud.getItems());
		validarImportes(solicitud.getImportes());
		validarIdentificacionConsumidorFinal(solicitud);

		if (solicitud.getTipoComprobante().esNota()
				&& (solicitud.getComprobantesAsociados() == null || solicitud.getComprobantesAsociados().isEmpty())) {
			throw new ComprobanteException("Las notas de crédito/débito requieren comprobantesAsociados");
		}
	}

	private void validarItems(List<ComprobanteSolicitud.Item> items) {
		if (items == null || items.isEmpty()) {
			throw new ComprobanteException("items no puede estar vacío");
		}
		for (ComprobanteSolicitud.Item item : items) {
			if (isBlank(item.getDescripcion())) {
				throw new ComprobanteException("Cada item debe tener descripción");
			}
			if (item.getCantidad() == null || item.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
				throw new ComprobanteException("Cada item debe tener cantidad mayor a cero");
			}
			if (item.getPrecioUnitario() == null || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) < 0) {
				throw new ComprobanteException("Cada item debe tener precioUnitario mayor o igual a cero");
			}
		}
	}

	private void validarImportes(ComprobanteSolicitud.Importes importes) {
		if (importes == null) {
			throw new ComprobanteException("importes es obligatorio");
		}
		if (importes.getTotal() == null || importes.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
			throw new ComprobanteException("importes.total debe ser mayor a cero");
		}
		BigDecimal suma = orZero(importes.getNeto()).add(orZero(importes.getIva())).add(orZero(importes.getTributos()))
				.add(orZero(importes.getExento())).add(orZero(importes.getNoGravado()));
		if (suma.compareTo(importes.getTotal()) != 0) {
			throw new ComprobanteException(
					"importes no coincide: neto + iva + tributos + exento + noGravado debe ser igual a total");
		}
	}

	// RG 4444 (umbral actualizado por RG 5866/2026): comprobantes B/C individuales no pueden
	// emitirse a Consumidor Final (DocTipo=99) si el total iguala o supera el umbral vigente.
	private void validarIdentificacionConsumidorFinal(ComprobanteSolicitud solicitud) {
		String letra = solicitud.getTipoComprobante().letra();
		if (!letra.equals("B") && !letra.equals("C")) {
			return;
		}
		TipoDocumento tipoDocumento = solicitud.getCliente().getTipoDocumento();
		if (tipoDocumento != null && tipoDocumento != TipoDocumento.CONSUMIDOR_FINAL) {
			return;
		}
		BigDecimal umbral = facturacion.umbralIdentificacionConsumidorFinal();
		BigDecimal total = solicitud.getImportes().getTotal();
		if (umbral != null && total.compareTo(umbral) >= 0) {
			throw new ComprobanteException("El total ($" + total + ") iguala o supera el umbral de identificación (RG 4444, $"
					+ umbral + "). Debe informarse un tipoDocumento y numeroDocumento reales del cliente.");
		}
	}

	private static BigDecimal orZero(BigDecimal valor) {
		return valor == null ? BigDecimal.ZERO : valor;
	}

	private static boolean isBlank(String valor) {
		return valor == null || valor.isBlank();
	}
}
