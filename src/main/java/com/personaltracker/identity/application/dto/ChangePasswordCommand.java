package com.personaltracker.identity.application.dto;

import com.personaltracker.shared.domain.UserId;

/** {@code currentSessionId} es la sesión desde la que se pide el cambio: es la única que sigue abierta. */
public record ChangePasswordCommand(
        UserId owner, String currentPassword, String newPassword, String currentSessionId) {}
