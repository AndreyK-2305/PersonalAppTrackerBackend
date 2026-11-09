package com.personaltracker.identity.application.dto;

import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;

/** Datos de la cuenta que se exponen al propio usuario. Nunca incluye el hash de la contraseña. */
public record UserProfile(
        UserId id,
        String email,
        String displayName,
        String timeZone,
        String givenNames,
        String surnames,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserProfile from(User user) {
        return new UserProfile(
                user.id(),
                user.email().value(),
                user.displayName(),
                user.timeZone().getId(),
                user.personName().givenNames(),
                user.personName().surnames(),
                user.createdAt(),
                user.lastLoginAt());
    }
}
