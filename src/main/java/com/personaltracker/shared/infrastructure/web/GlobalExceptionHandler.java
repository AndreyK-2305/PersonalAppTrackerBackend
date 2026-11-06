package com.personaltracker.shared.infrastructure.web;

import com.personaltracker.shared.domain.DomainException;
import com.personaltracker.shared.domain.NotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción a un {@link ProblemDetail} (RFC 9457) con un {@code code} estable, un
 * mensaje en español y el identificador de correlación. Nunca expone detalles internos.
 *
 * <p>Spring Security delega aquí sus errores 401/403, de modo que todas las respuestas de error de
 * la API tienen el mismo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final ProblemType FALLBACK =
            new ProblemType("error", "Error", "No fue posible procesar la solicitud.");

    private static final Map<Integer, ProblemType> PROBLEM_TYPES = Map.ofEntries(
            Map.entry(400, new ProblemType("bad_request", "Solicitud inválida",
                    "La solicitud no es válida.")),
            Map.entry(401, new ProblemType("unauthorized", "No autenticado",
                    "Debes iniciar sesión para acceder a este recurso.")),
            Map.entry(403, new ProblemType("forbidden", "Acceso denegado",
                    "No tienes permiso para realizar esta operación.")),
            Map.entry(404, new ProblemType("not_found", "Recurso no encontrado",
                    "El recurso solicitado no existe.")),
            Map.entry(405, new ProblemType("method_not_allowed", "Método no permitido",
                    "El método HTTP no está permitido para este recurso.")),
            Map.entry(406, new ProblemType("not_acceptable", "Formato no aceptable",
                    "No es posible responder en el formato solicitado.")),
            Map.entry(409, new ProblemType("conflict", "Conflicto",
                    "La operación entra en conflicto con el estado actual del recurso.")),
            Map.entry(413, new ProblemType("payload_too_large", "Contenido demasiado grande",
                    "El contenido de la solicitud supera el tamaño permitido.")),
            Map.entry(415, new ProblemType("unsupported_media_type", "Tipo de contenido no soportado",
                    "El tipo de contenido de la solicitud no está soportado.")),
            Map.entry(422, new ProblemType("business_rule_violation", "Regla de negocio incumplida",
                    "La operación no cumple las reglas de negocio.")),
            Map.entry(500, new ProblemType("internal_error", "Error interno",
                    "Ocurrió un error inesperado. Intenta de nuevo más tarde.")),
            Map.entry(503, new ProblemType("service_unavailable", "Servicio no disponible",
                    "El servicio no está disponible en este momento. Intenta de nuevo más tarde.")));

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomainRule(DomainException ex) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation_error",
                "La solicitud contiene datos inválidos.");
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Punto común de las excepciones estándar de Spring MVC: reemplaza el cuerpo genérico (en
     * inglés) por el formato de la API, salvo que ya venga construido por este manejador.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        Object apiBody = isApiProblem(body) ? body : problem(statusCode);
        return super.handleExceptionInternal(ex, apiBody, headers, statusCode, request);
    }

    private static boolean isApiProblem(Object body) {
        return body instanceof ProblemDetail problem
                && problem.getProperties() != null
                && problem.getProperties().containsKey("code");
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status) {
        return ResponseEntity.status(status).body(problem(status));
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problem(status, code, detail));
    }

    private static ProblemDetail problem(HttpStatusCode status) {
        ProblemType type = typeOf(status);
        return problem(status, type.code(), type.detail());
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(typeOf(status).title());
        problem.setProperty("code", code);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    private static ProblemType typeOf(HttpStatusCode status) {
        return PROBLEM_TYPES.getOrDefault(status.value(), FALLBACK);
    }

    private record ProblemType(String code, String title, String detail) {}

    /** Elemento de la propiedad {@code errors} en las respuestas {@code validation_error}. */
    public record FieldViolation(String field, String message) {}
}
