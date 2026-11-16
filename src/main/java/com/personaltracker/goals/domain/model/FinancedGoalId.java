package com.personaltracker.goals.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de una meta financiada. */
public record FinancedGoalId(UUID value) {

    public FinancedGoalId {
        Objects.requireNonNull(value, "value");
    }

    public static FinancedGoalId newId() {
        return new FinancedGoalId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
