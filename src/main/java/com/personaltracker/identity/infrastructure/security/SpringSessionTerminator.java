package com.personaltracker.identity.infrastructure.security;

import com.personaltracker.identity.application.port.out.SessionTerminator;
import com.personaltracker.shared.domain.UserId;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Cierra sesiones usando el índice por usuario de Spring Session (nombre del principal = id de usuario). */
@Component
class SpringSessionTerminator implements SessionTerminator {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SpringSessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void terminateAllExcept(UserId user, String sessionIdToKeep) {
        sessions.findByPrincipalName(user.toString()).keySet().stream()
                .filter(sessionId -> !sessionId.equals(sessionIdToKeep))
                .forEach(sessions::deleteById);
    }
}
