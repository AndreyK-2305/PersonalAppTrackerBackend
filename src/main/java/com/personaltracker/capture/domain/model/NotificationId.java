package com.personaltracker.capture.domain.model;

import java.util.Objects;
import java.util.UUID;

public record NotificationId(UUID value) {

    public NotificationId {
        Objects.requireNonNull(value, "value");
    }

    public static NotificationId newId() {
        return new NotificationId(UUID.randomUUID());
    }
}
