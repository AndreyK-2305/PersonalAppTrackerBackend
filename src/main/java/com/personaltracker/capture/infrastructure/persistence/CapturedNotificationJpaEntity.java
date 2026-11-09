package com.personaltracker.capture.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code capture.notifications}. Separada del modelo de dominio; se mapea a mano. */
@Entity
@Table(schema = "capture", name = "notifications")
class CapturedNotificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String app;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "review_reason", length = 30)
    private String reviewReason;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "paired_with")
    private UUID pairedWith;

    @Column(name = "product_id")
    private UUID productId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(length = 20)
    private String source;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "raw_purged_at")
    private Instant rawPurgedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Bloqueo optimista: null indica a Spring Data que la entidad es nueva.
    @Version
    private Long version;

    protected CapturedNotificationJpaEntity() {}

    CapturedNotificationJpaEntity(UUID id) {
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

    String getApp() {
        return app;
    }

    void setApp(String app) {
        this.app = app;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    String getBody() {
        return body;
    }

    void setBody(String body) {
        this.body = body;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }

    void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    String getContentHash() {
        return contentHash;
    }

    void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getReviewReason() {
        return reviewReason;
    }

    void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    UUID getTransactionId() {
        return transactionId;
    }

    void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    UUID getPairedWith() {
        return pairedWith;
    }

    void setPairedWith(UUID pairedWith) {
        this.pairedWith = pairedWith;
    }

    UUID getProductId() {
        return productId;
    }

    void setProductId(UUID productId) {
        this.productId = productId;
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

    String getSource() {
        return source;
    }

    void setSource(String source) {
        this.source = source;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    Instant getRawPurgedAt() {
        return rawPurgedAt;
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
