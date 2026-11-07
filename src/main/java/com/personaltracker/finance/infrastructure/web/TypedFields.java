package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.shared.infrastructure.web.GlobalExceptionHandler.FieldViolation;
import com.personaltracker.shared.infrastructure.web.RequestValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida los campos de un cuerpo plano que dependen de su tipo (de producto o de movimiento): los
 * que el tipo exige y los que no le aplican. Un texto en blanco cuenta como ausente. Reúne todos los
 * errores para responderlos juntos como {@code validation_error}.
 */
final class TypedFields {

    private final String typeLabel;
    private final List<FieldViolation> violations = new ArrayList<>();

    /** {@code typeLabel} completa los mensajes, por ejemplo "tipo de producto". */
    TypedFields(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    TypedFields required(String field, Object value) {
        if (isMissing(value)) {
            violations.add(new FieldViolation(field, "es obligatorio para este " + typeLabel));
        }
        return this;
    }

    TypedFields absent(String field, Object value) {
        if (!isMissing(value)) {
            violations.add(new FieldViolation(field, "no aplica a este " + typeLabel));
        }
        return this;
    }

    TypedFields reject(String field, String message) {
        violations.add(new FieldViolation(field, message));
        return this;
    }

    void throwIfAny() {
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
    }

    private static boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }
}
