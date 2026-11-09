package com.personaltracker.identity.application.dto;

import com.personaltracker.shared.domain.UserId;

/**
 * Actualización parcial del perfil: un campo {@code null} no se modifica. En {@code givenNames} y
 * {@code surnames}, un texto vacío los borra.
 */
public record UpdateProfileCommand(
        UserId owner, String displayName, String timeZone, String givenNames, String surnames) {

    public UpdateProfileCommand(UserId owner, String displayName, String timeZone) {
        this(owner, displayName, timeZone, null, null);
    }
}
