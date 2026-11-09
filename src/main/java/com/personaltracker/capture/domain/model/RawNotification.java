package com.personaltracker.capture.domain.model;

import java.time.Instant;
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
}
