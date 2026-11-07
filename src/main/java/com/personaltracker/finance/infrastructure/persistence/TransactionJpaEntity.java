package com.personaltracker.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representación persistente de la jerarquía {@code Transaction}: una fila por movimiento y
 * {@code type} como discriminador. Un ajuste usa el destino si aumenta el saldo y el origen si lo
 * disminuye. Solo la usa {@link TransactionPersistenceAdapter}.
 */
@Entity
@Table(schema = "finance", name = "transactions")
class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 200)
    private String description;

    @Column(length = 100)
    private String merchant;

    @Column(length = 20)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Bloqueo optimista: null indica a Spring Data que la entidad es nueva.
    @Version
    private Long version;

    protected TransactionJpaEntity() {}

    TransactionJpaEntity(UUID id) {
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

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    LocalDate getTransactionDate() {
        return transactionDate;
    }

    void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    UUID getSourceAccountId() {
        return sourceAccountId;
    }

    void setSourceAccountId(UUID sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    void setDestinationAccountId(UUID destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    String getMerchant() {
        return merchant;
    }

    void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    String getChannel() {
        return channel;
    }

    void setChannel(String channel) {
        this.channel = channel;
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
