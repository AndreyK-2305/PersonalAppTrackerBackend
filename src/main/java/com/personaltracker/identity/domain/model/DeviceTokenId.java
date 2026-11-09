package com.personaltracker.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

public record DeviceTokenId(UUID value) {

    public DeviceTokenId {
        Objects.requireNonNull(value, "value");
    }

    public static DeviceTokenId newId() {
        return new DeviceTokenId(UUID.randomUUID());
    }
}
