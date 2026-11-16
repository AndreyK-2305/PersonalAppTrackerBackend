package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidContributionException;
import com.personaltracker.goals.domain.exception.InvalidContributionException.Rule;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Abono a una meta de ahorro: una marca con fecha y valor, no un movimiento de Finanzas (ADR-0018). */
public record SavingsContribution(
        UUID id, SavingsGoalId goalId, UserId owner, LocalDate date, Money amount, Instant createdAt) {

    /** Antigüedad máxima de la fecha de un abono respecto a hoy. */
    public static final int MAX_AGE_YEARS = 100;

    public SavingsContribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new InvalidContributionException(Rule.AMOUNT, "El abono debe ser mayor que cero.");
        }
    }
}
