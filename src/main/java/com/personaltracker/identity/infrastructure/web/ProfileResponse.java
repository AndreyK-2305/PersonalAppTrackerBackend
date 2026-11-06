package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.application.dto.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** {@code lastLoginAt} es nulo si la cuenta nunca ha iniciado sesión. */
@Schema(requiredProperties = {"id", "email", "displayName", "timeZone", "createdAt"})
record ProfileResponse(
        UUID id,
        String email,
        String displayName,
        String timeZone,
        Instant createdAt,
        Instant lastLoginAt) {

    static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.id().value(),
                profile.email(),
                profile.displayName(),
                profile.timeZone(),
                profile.createdAt(),
                profile.lastLoginAt());
    }
}
