package com.personaltracker.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación persistente de la jerarquía {@code Account}: una fila por producto y {@code kind}
 * como discriminador. Solo la usa {@link AccountPersistenceAdapter}.
 */
@Entity
@Table(schema = "finance", name = "accounts")
class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 60)
    private String institution;

    @Column(name = "last_four_digits", length = 4)
    private String lastFourDigits;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Bloqueo optimista: null indica a Spring Data que la entidad es nueva.
    @Version
    private Long version;

    protected AccountJpaEntity() {}

    AccountJpaEntity(UUID id) {
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

    String getKind() {
        return kind;
    }

    void setKind(String kind) {
        this.kind = kind;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getInstitution() {
        return institution;
    }

    void setInstitution(String institution) {
        this.institution = institution;
    }

    String getLastFourDigits() {
        return lastFourDigits;
    }

    void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    BigDecimal getCreditLimit() {
        return creditLimit;
    }

    void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
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
