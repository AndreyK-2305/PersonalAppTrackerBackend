package com.personaltracker.identity.application.port.out;

import com.personaltracker.shared.domain.UserId;

/** Cierra sesiones abiertas de un usuario (por ejemplo, al cambiar la contraseña). */
public interface SessionTerminator {

    /** Cierra todas las sesiones del usuario excepto la indicada, que puede ser {@code null}. */
    void terminateAllExcept(UserId user, String sessionIdToKeep);
}
