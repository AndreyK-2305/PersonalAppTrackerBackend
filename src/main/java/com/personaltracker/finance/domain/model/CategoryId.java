package com.personaltracker.finance.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un categoría. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId newId() {
        return new CategoryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
