package com.personaltracker.capture.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code capture.own_keys}: una llave Bre-B propia y el producto al que llega. */
@Entity
@Table(schema = "capture", name = "own_keys")
class OwnKeyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OwnKeyJpaEntity() {}

    OwnKeyJpaEntity(UUID id, UUID userId, String keyValue, UUID productId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.keyValue = keyValue;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getKeyValue() {
        return keyValue;
    }

    UUID getProductId() {
        return productId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
