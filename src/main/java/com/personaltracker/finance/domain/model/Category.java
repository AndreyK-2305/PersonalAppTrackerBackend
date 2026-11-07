package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.CategoryArchivedException;
import com.personaltracker.finance.domain.exception.CategoryDepthExceededException;
import com.personaltracker.finance.domain.exception.CategoryKindMismatchException;
import com.personaltracker.finance.domain.exception.InvalidCategoryNameException;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Categoría o subcategoría de ingresos o de gastos (ADR-0014). Hay dos niveles como máximo: una
 * subcategoría hereda el tipo de su categoría principal y no puede tener subcategorías propias.
 */
public final class Category {

    public static final int MAX_NAME_LENGTH = 50;

    private final CategoryId id;
    private final UserId owner;
    private final CategoryKind kind;
    private String name;
    private final CategoryId parentId;
    private Instant archivedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(
            CategoryId id,
            UserId owner,
            CategoryKind kind,
            String name,
            CategoryId parentId,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.parentId = parentId;
        this.archivedAt = archivedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Category createTopLevel(CategoryId id, UserId owner, CategoryKind kind, String name, Instant now) {
        return new Category(id, owner, kind, validName(name), null, null, now, now);
    }

    /** Crea una subcategoría con el propietario y el tipo de su categoría principal. */
    public static Category createChild(CategoryId id, Category parent, String name, Instant now) {
        if (!parent.isTopLevel()) {
            throw new CategoryDepthExceededException();
        }
        if (parent.isArchived()) {
            throw new CategoryArchivedException();
        }
        return new Category(id, parent.owner, parent.kind, validName(name), parent.id, null, now, now);
    }

    /** Reconstruye una categoría persistida tal como estaba. */
    public static Category restore(
            CategoryId id,
            UserId owner,
            CategoryKind kind,
            String name,
            CategoryId parentId,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new Category(id, owner, kind, name, parentId, archivedAt, createdAt, updatedAt);
    }

    public boolean isTopLevel() {
        return parentId == null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Comprueba que la categoría se puede asignar a un movimiento del tipo indicado. */
    public void ensureUsableFor(CategoryKind expected) {
        if (kind != expected) {
            throw new CategoryKindMismatchException();
        }
        if (isArchived()) {
            throw new CategoryArchivedException();
        }
    }

    public void rename(String newName, Instant now) {
        name = validName(newName);
        updatedAt = now;
    }

    /** Archivar una categoría principal también archiva sus subcategorías; eso lo coordina la aplicación. */
    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
            updatedAt = now;
        }
    }

    public void reactivate(Instant now) {
        if (archivedAt != null) {
            archivedAt = null;
            updatedAt = now;
        }
    }

    private static String validName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidCategoryNameException();
        }
        return trimmed;
    }

    public CategoryId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public CategoryKind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    /** Nulo en una categoría principal. */
    public CategoryId parentId() {
        return parentId;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
