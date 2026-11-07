package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.InvalidInstitutionException;

/** Entidad que emite el producto (por ejemplo, Bancolombia o Nequi), tal como la escribe el usuario. */
public record Institution(String name) {

    public static final int MAX_LENGTH = 60;

    public Institution {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new InvalidInstitutionException();
        }
        name = trimmed;
    }
}
