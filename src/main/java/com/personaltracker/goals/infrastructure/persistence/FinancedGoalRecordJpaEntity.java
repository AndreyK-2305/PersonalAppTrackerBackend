package com.personaltracker.goals.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de {@code goals.financed_goal_records}. Los registros no se editan: se agregan o se deshacen. */
@Entity
@Table(schema = "goals", name = "financed_goal_records")
class FinancedGoalRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "prepayment_mode", length = 20)
    private String prepaymentMode;

    @Column(name = "annual_rate", precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(name = "actual_installment", precision = 19, scale = 2)
    private BigDecimal actualInstallment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinancedGoalRecordJpaEntity() {}

    FinancedGoalRecordJpaEntity(UUID id, UUID goalId, UUID userId, int sequence, String kind, LocalDate recordDate,
            String currency, BigDecimal amount, String prepaymentMode, BigDecimal annualRate,
            BigDecimal actualInstallment, Instant createdAt) {
        this.id = id;
        this.goalId = goalId;
        this.userId = userId;
        this.sequence = sequence;
        this.kind = kind;
        this.recordDate = recordDate;
        this.currency = currency;
        this.amount = amount;
        this.prepaymentMode = prepaymentMode;
        this.annualRate = annualRate;
        this.actualInstallment = actualInstallment;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    int getSequence() {
        return sequence;
    }

    String getKind() {
        return kind;
    }

    LocalDate getRecordDate() {
        return recordDate;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getPrepaymentMode() {
        return prepaymentMode;
    }

    BigDecimal getAnnualRate() {
        return annualRate;
    }

    BigDecimal getActualInstallment() {
        return actualInstallment;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
