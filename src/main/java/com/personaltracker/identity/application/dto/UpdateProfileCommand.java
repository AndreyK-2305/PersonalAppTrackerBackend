package com.personaltracker.identity.application.dto;

import com.personaltracker.shared.domain.UserId;

/** Actualización parcial del perfil: un campo {@code null} no se modifica. */
public record UpdateProfileCommand(UserId owner, String displayName, String timeZone) {}
