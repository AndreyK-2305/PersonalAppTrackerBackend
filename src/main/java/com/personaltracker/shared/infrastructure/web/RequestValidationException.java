package com.personaltracker.shared.infrastructure.web;

import com.personaltracker.shared.infrastructure.web.GlobalExceptionHandler.FieldViolation;
import java.util.List;

/**
 * Datos inválidos que detecta un adaptador web después de Bean Validation, por ejemplo campos que
 * dependen del tipo de recurso. Se responde igual que un error de Bean Validation: 400
 * {@code validation_error} con la lista {@code errors}.
 */
public class RequestValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public RequestValidationException(List<FieldViolation> violations) {
        super("La solicitud contiene datos inválidos");
        this.violations = List.copyOf(violations);
    }

    public static RequestValidationException of(String field, String message) {
        return new RequestValidationException(List.of(new FieldViolation(field, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
