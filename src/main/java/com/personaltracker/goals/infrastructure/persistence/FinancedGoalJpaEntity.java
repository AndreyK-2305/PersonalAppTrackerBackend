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

/** Fila de {@code goals.financed_goals}. */
@Entity
@Table(schema = "goals", name = "financed_goals")
class FinancedGoalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "asset_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal assetValue;

    @Column(name = "down_payment", nullable = false, precision = 19, scale = 2)
    private BigDecimal downPayment;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "annual_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(nullable = false)
    private int installments;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Column(name = "actual_installment", precision = 19, scale = 2)
    private BigDecimal actualInstallment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected FinancedGoalJpaEntity() {}

    FinancedGoalJpaEntity(UUID id) {
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

    BigDecimal getAssetValue() {
        return assetValue;
    }

    void setAssetValue(BigDecimal assetValue) {
        this.assetValue = assetValue;
    }

    BigDecimal getDownPayment() {
        return downPayment;
    }

    void setDownPayment(BigDecimal downPayment) {
        this.downPayment = downPayment;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    BigDecimal getAnnualRate() {
        return annualRate;
    }

    void setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    int getInstallments() {
        return installments;
    }

    void setInstallments(int installments) {
        this.installments = installments;
    }

    LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    void setFirstDueDate(LocalDate firstDueDate) {
        this.firstDueDate = firstDueDate;
    }

    BigDecimal getActualInstallment() {
        return actualInstallment;
    }

    void setActualInstallment(BigDecimal actualInstallment) {
        this.actualInstallment = actualInstallment;
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
