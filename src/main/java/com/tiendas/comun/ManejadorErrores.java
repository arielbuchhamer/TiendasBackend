package com.tiendas.comun;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones a respuestas RFC 9457 (application/problem+json).
 * Nunca devuelve mensajes internos (SQL, stack traces) al cliente: eso queda solo en el log.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	@ExceptionHandler(NoEncontradoException.class)
	ProblemDetail noEncontrado(NoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ReglaNegocioException.class)
	ProblemDetail reglaNegocio(ReglaNegocioException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	ProblemDetail conflicto(ConflictoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail modificacionConcurrente(ObjectOptimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"El recurso fue modificado por otra operación. Volvé a cargarlo y reintentá.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail integridad(DataIntegrityViolationException e) {
		log.info("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"La operación viola una restricción de datos (registro duplicado o en uso por otros registros).");
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail propiedadInvalida(PropertyReferenceException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Propiedad inválida: " + e.getPropertyName());
	}

	@ExceptionHandler(ServicioExternoException.class)
	ProblemDetail servicioExterno(ServicioExternoException e) {
		log.error("Error en servicio externo: {}", e.getMessage(), e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail inesperado(Exception e) {
		log.error("Error no controlado", e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
	}

	/** Agrega el detalle de cada campo inválido en la propiedad "errores". */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errores = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
		problema.setProperty("errores", errores);
		return ResponseEntity.badRequest().body(problema);
	}
}
