package com.personaltracker.goals.domain.model;

import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Cambio de la rentabilidad de una meta de ahorro: la tasa EA aplica desde {@code effectiveFrom}, que
 * es el día siguiente al cambio. Una tasa de 0 apaga la rentabilidad (ADR-0018).
 */
public record SavingsRateChange(
        UUID id, SavingsGoalId goalId, UserId owner, LocalDate effectiveFrom, AnnualRate rate, Instant createdAt) {

    public SavingsRateChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
