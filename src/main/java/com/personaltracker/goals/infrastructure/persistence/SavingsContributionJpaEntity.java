package com.personaltracker.goals.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de {@code goals.savings_contributions}. Los abonos no se editan: se borran y se marcan de nuevo. */
@Entity
@Table(schema = "goals", name = "savings_contributions")
class SavingsContributionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavingsContributionJpaEntity() {}

    SavingsContributionJpaEntity(UUID id, UUID goalId, UUID userId, LocalDate contributionDate, BigDecimal amount,
            String currency, Instant createdAt) {
        this.id = id;
        this.goalId = goalId;
        this.userId = userId;
        this.contributionDate = contributionDate;
        this.amount = amount;
        this.currency = currency;
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

    LocalDate getContributionDate() {
        return contributionDate;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
