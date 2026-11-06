package com.personaltracker.shared.infrastructure.web;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Construye las respuestas de error de la API (RFC 9457) con el formato común: título en español,
 * {@code code} estable e identificador de correlación (ADR-0008). Los manejadores de excepciones
 * de cada módulo la usan para no duplicar el formato.
 */
public final class ApiProblems {

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
            Map.entry(429, new ProblemType("too_many_requests", "Demasiadas solicitudes",
                    "Has realizado demasiadas solicitudes. Intenta de nuevo más tarde.")),
            Map.entry(500, new ProblemType("internal_error", "Error interno",
                    "Ocurrió un error inesperado. Intenta de nuevo más tarde.")),
            Map.entry(503, new ProblemType("service_unavailable", "Servicio no disponible",
                    "El servicio no está disponible en este momento. Intenta de nuevo más tarde.")));

    private ApiProblems() {}

    /** Problema genérico del estado HTTP, con su código y mensaje estándar. */
    public static ProblemDetail of(HttpStatusCode status) {
        ProblemType type = typeOf(status);
        return of(status, type.code(), type.detail());
    }

    /** Problema con un código y un mensaje específicos (por ejemplo, de una excepción de dominio). */
    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(typeOf(status).title());
        problem.setProperty("code", code);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatusCode status) {
        return ResponseEntity.status(status).body(of(status));
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatusCode status, String code, String detail) {
        return ResponseEntity.status(status).body(of(status, code, detail));
    }

    /** Indica si el cuerpo ya tiene el formato de la API (incluye la propiedad {@code code}). */
    static boolean isApiProblem(Object body) {
        return body instanceof ProblemDetail problem
                && problem.getProperties() != null
                && problem.getProperties().containsKey("code");
    }

    private static ProblemType typeOf(HttpStatusCode status) {
        return PROBLEM_TYPES.getOrDefault(status.value(), FALLBACK);
    }

    private record ProblemType(String code, String title, String detail) {}
}
