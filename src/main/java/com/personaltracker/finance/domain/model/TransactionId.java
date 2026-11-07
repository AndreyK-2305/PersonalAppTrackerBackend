package com.personaltracker.finance.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un movimiento. */
public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
