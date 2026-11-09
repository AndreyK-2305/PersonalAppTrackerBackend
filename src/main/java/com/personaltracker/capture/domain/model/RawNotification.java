package com.personaltracker.capture.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Notificación tal como la envía el atajo del iPhone: la app de origen (por ejemplo, "Mensajes" o
 * "Rappi"), el título, el texto y la hora en que llegó al teléfono.
 */
public record RawNotification(String app, String title, String body, Instant receivedAt) {

    public RawNotification {
        app = app == null ? "" : app;
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * SHA-256 en hexadecimal de la app, el título, el texto y la hora de recepción. Dos envíos con el
     * mismo hash son un reenvío exacto del atajo, no dos compras (ADR-0017).
     */
    public String contentHash() {
        String content = String.join("\u0000", app, title, body, receivedAt.toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible", impossible);
        }
    }
}
