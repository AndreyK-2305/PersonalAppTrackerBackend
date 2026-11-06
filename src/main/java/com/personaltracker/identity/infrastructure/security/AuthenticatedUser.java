package com.personaltracker.identity.infrastructure.security;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Principal guardado en la sesión. Solo contiene el identificador del usuario: el nombre de la
 * autenticación es ese identificador, que Spring Session usa para indexar las sesiones por usuario.
 */
public record AuthenticatedUser(UUID userId) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return userId.toString();
    }
}
