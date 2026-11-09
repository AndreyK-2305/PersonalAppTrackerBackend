package com.personaltracker.identity.domain.model;

import com.personaltracker.identity.domain.exception.InvalidDeviceTokenException;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Token con el que un dispositivo (el atajo del iPhone) llama a los endpoints de dispositivo en
 * nombre del usuario (ADR-0017). Se gestiona en Ajustes → Conexiones; revocarlo es definitivo.
 */
public final class DeviceToken {

    public static final int MAX_NAME_LENGTH = 60;

    private final DeviceTokenId id;
    private final UserId owner;
    private final String name;
    private final String secretHash;
    private final Set<DeviceScope> scopes;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    private DeviceToken(
            DeviceTokenId id,
            UserId owner,
            String name,
            String secretHash,
            Set<DeviceScope> scopes,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.secretHash = Objects.requireNonNull(secretHash, "secretHash");
        this.scopes = Set.copyOf(scopes);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
    }

    /** Crea un token con el hash de un secreto recién generado; exige al menos un permiso. */
    public static DeviceToken issue(
            DeviceTokenId id, UserId owner, String name, String secretHash, Set<DeviceScope> scopes, Instant now) {
        if (scopes == null || scopes.isEmpty()) {
            throw new InvalidDeviceTokenException();
        }
        return new DeviceToken(id, owner, validName(name), secretHash, scopes, now, null, null);
    }

    public static DeviceToken restore(
            DeviceTokenId id,
            UserId owner,
            String name,
            String secretHash,
            Set<DeviceScope> scopes,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {
        return new DeviceToken(id, owner, name, secretHash, scopes, createdAt, lastUsedAt, revokedAt);
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean allows(DeviceScope scope) {
        return isActive() && scopes.contains(scope);
    }

    public void recordUse(Instant now) {
        lastUsedAt = now;
    }

    /** Revocar dos veces no cambia la fecha de la primera revocación. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    private static String validName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidDeviceTokenException();
        }
        return trimmed;
    }

    public DeviceTokenId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String secretHash() {
        return secretHash;
    }

    public Set<DeviceScope> scopes() {
        return scopes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
