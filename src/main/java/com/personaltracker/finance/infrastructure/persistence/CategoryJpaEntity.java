package com.personaltracker.finance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Representación persistente de una categoría. Solo la usa {@link CategoryPersistenceAdapter}. */
@Entity
@Table(schema = "finance", name = "categories")
class CategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Bloqueo optimista: null indica a Spring Data que la entidad es nueva.
    @Version
    private Long version;

    protected CategoryJpaEntity() {}

    CategoryJpaEntity(UUID id) {
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

    UUID getParentId() {
        return parentId;
    }

    void setParentId(UUID parentId) {
        this.parentId = parentId;
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
