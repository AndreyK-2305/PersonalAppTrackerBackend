package com.personaltracker.goals.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de una meta de ahorro. */
public record SavingsGoalId(UUID value) {

    public SavingsGoalId {
        Objects.requireNonNull(value, "value");
    }

    public static SavingsGoalId newId() {
        return new SavingsGoalId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
