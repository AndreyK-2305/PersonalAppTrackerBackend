package com.personaltracker.identity.domain.model;

import com.personaltracker.identity.domain.exception.InvalidPersonNameException;

/**
 * Nombres y apellidos legales del usuario, distintos del nombre visible. La captura los usa para
 * reconocer transferencias a sí mismo (ADR-0017). Ambos son opcionales: un texto en blanco es nulo.
 */
public record PersonName(String givenNames, String surnames) {

    public static final int MAX_LENGTH = 100;
    public static final PersonName EMPTY = new PersonName(null, null);

    public PersonName {
        givenNames = optional(givenNames);
        surnames = optional(surnames);
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidPersonNameException();
        }
        return trimmed;
    }
}
