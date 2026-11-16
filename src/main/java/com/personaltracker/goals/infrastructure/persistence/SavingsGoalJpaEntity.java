package com.personaltracker.goals.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de {@code goals.savings_goals}. */
@Entity
@Table(schema = "goals", name = "savings_goals")
class SavingsGoalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "plan_kind", nullable = false, length = 20)
    private String planKind;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "monthly_amount", precision = 19, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected SavingsGoalJpaEntity() {}

    SavingsGoalJpaEntity(UUID id) {
        this.id = id;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    BigDecimal getTargetAmount() {
        return targetAmount;
    }

    void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    String getPlanKind() {
        return planKind;
    }

    void setPlanKind(String planKind) {
        this.planKind = planKind;
    }

    LocalDate getTargetDate() {
        return targetDate;
    }

    void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
