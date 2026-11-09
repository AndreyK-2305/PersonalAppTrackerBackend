package com.personaltracker.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code identity.device_tokens}. */
@Entity
@Table(schema = "identity", name = "device_tokens")
class DeviceTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(nullable = false, length = 200)
    private String scopes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Bloqueo optimista: null indica a Spring Data que la entidad es nueva.
    @Version
    private Long version;

    protected DeviceTokenJpaEntity() {}

    DeviceTokenJpaEntity(UUID id) {
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

    String getSecretHash() {
        return secretHash;
    }

    void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    String getScopes() {
        return scopes;
    }

    void setScopes(String scopes) {
        this.scopes = scopes;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
