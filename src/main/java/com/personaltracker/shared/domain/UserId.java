package com.personaltracker.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de un usuario. Todos los módulos lo usan para expresar la propiedad de sus
 * recursos; siempre se obtiene de la sesión, nunca de datos enviados por el cliente (ADR-0003).
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
