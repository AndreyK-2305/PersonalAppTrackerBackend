package com.personaltracker.capture.domain.model;

import com.personaltracker.capture.domain.exception.InvalidOwnKeyException;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Llave Bre-B propia (Ajustes → Perfil, ADR-0017): una transferencia a esta llave es una
 * transferencia hacia {@code productId}. La llave se guarda normalizada (sin espacios a los lados y en
 * minúsculas) para compararla con la del SMS. Es un dato personal, no una credencial bancaria.
 */
public record OwnKey(UUID id, UserId owner, String key, UUID productId, Instant createdAt) {

    public static final int MAX_LENGTH = 100;

    public OwnKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        key = normalize(key);
        if (key.isEmpty() || key.length() > MAX_LENGTH) {
            throw new InvalidOwnKeyException();
        }
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
