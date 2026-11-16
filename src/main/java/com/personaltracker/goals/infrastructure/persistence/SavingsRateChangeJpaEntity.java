package com.personaltracker.goals.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de {@code goals.savings_rate_changes}: tasa EA como fracción desde {@code effective_from}. */
@Entity
@Table(schema = "goals", name = "savings_rate_changes")
class SavingsRateChangeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "annual_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavingsRateChangeJpaEntity() {}

    SavingsRateChangeJpaEntity(UUID id, UUID goalId, UUID userId, LocalDate effectiveFrom, BigDecimal annualRate,
            Instant createdAt) {
        this.id = id;
        this.goalId = goalId;
        this.userId = userId;
        this.effectiveFrom = effectiveFrom;
        this.annualRate = annualRate;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getGoalId() {
        return goalId;
    }

    UUID getUserId() {
        return userId;
    }

    LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    BigDecimal getAnnualRate() {
        return annualRate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
