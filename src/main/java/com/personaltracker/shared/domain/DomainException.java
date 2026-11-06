package com.personaltracker.shared.domain;

import java.util.Objects;

/**
 * Violación de una regla de negocio. La API la expone como 422 con el {@code code} estable y el
 * mensaje, que debe estar en español y ser apto para mostrarse al usuario.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }
}
