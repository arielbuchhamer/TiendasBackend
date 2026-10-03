package com.tiendas.facturacion;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@FacturacionHabilitada
class ComprobanteController {

	private final ComprobanteService comprobanteService;

	ComprobanteController(ComprobanteService comprobanteService) {
		this.comprobanteService = comprobanteService;
	}

	/** Emisión completa: el request trae todos los datos fiscales (ver ComprobanteSolicitud). */
	@PostMapping("/admin/comprobantes")
	@ResponseStatus(HttpStatus.CREATED)
	Comprobante generar(@RequestBody ComprobanteSolicitud solicitud) {
		return comprobanteService.generar(solicitud);
	}

	/** Emisión desde el panel: solo cliente e items; el resto sale de la configuración de la tienda. */
	@PostMapping("/admin/comprobantes/manual")
	@ResponseStatus(HttpStatus.CREATED)
	Comprobante generarManual(@RequestBody ComprobanteManualSolicitud solicitud) {
		return comprobanteService.generarManual(solicitud);
	}

	@GetMapping("/admin/comprobantes")
	Page<Comprobante> listar(@PageableDefault(size = 20) Pageable pagina) {
		return comprobanteService.listar(pagina);
	}

	@GetMapping("/admin/comprobantes/{id}")
	Comprobante obtener(@PathVariable Long id) {
		return comprobanteService.obtener(id);
	}

	@GetMapping("/admin/comprobantes/idempotency/{idempotencyKey}")
	Comprobante obtenerPorIdempotencyKey(@PathVariable String idempotencyKey) {
		return comprobanteService.obtenerPorIdempotencyKey(idempotencyKey);
	}

	/**
	 * PDF público por código (UUID aleatorio, no enumerable) para poder enviárselo al comprador.
	 * No se cachea en intermediarios porque contiene datos personales.
	 */
	@GetMapping("/comprobantes/{codigo}/pdf")
	ResponseEntity<byte[]> pdf(@PathVariable UUID codigo) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprobante-" + codigo + ".pdf\"")
				.body(comprobanteService.obtenerPdf(codigo));
	}
}
