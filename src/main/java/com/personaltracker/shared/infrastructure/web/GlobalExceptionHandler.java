package com.personaltracker.shared.infrastructure.web;

import com.personaltracker.shared.domain.ConflictException;
import com.personaltracker.shared.domain.DomainException;
import com.personaltracker.shared.domain.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
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
 * la API tienen el mismo formato. Los módulos pueden declarar manejadores propios con mayor
 * precedencia para sus excepciones específicas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return ApiProblems.response(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        return ApiProblems.response(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomainRule(DomainException ex) {
        return ApiProblems.response(HttpStatus.UNPROCESSABLE_CONTENT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ApiProblems.response(HttpStatus.UNAUTHORIZED);
    }

    /** Falta el token CSRF o no es válido: el cliente debe obtener uno nuevo y reintentar. */
    @ExceptionHandler(CsrfException.class)
    ResponseEntity<ProblemDetail> handleCsrf(CsrfException ex) {
        return ApiProblems.response(HttpStatus.FORBIDDEN, "csrf_invalid",
                "La verificación de seguridad de la solicitud falló. Recarga la página e intenta de nuevo.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ApiProblems.response(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return ApiProblems.response(HttpStatus.INTERNAL_SERVER_ERROR);
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
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, "validation_error",
                "La solicitud contiene datos inválidos.");
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Punto común de las excepciones estándar de Spring MVC: reemplaza el cuerpo genérico (en
     * inglés) por el formato de la API, salvo que ya venga construido con ese formato.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        Object apiBody = ApiProblems.isApiProblem(body) ? body : ApiProblems.of(statusCode);
        return super.handleExceptionInternal(ex, apiBody, headers, statusCode, request);
    }

    /** Elemento de la propiedad {@code errors} en las respuestas {@code validation_error}. */
    public record FieldViolation(String field, String message) {}
}
