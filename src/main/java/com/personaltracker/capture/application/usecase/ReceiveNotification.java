package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recibe una notificación del atajo del iPhone y la deja en la cola (ADR-0017). No la analiza: el
 * trabajador de la cola la procesa pasada la espera de 2 minutos. Un reenvío exacto (mismo
 * contenido y misma hora de recepción) se ignora sin error.
 */
@Service
public class ReceiveNotification {

    private final CapturedNotificationRepository notifications;
    private final Clock clock;

    public ReceiveNotification(CapturedNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Devuelve {@code false} si era un reenvío exacto y no se guardó nada. */
    @Transactional
    public boolean execute(UserId owner, RawNotification raw) {
        if (notifications.existsByContent(owner, raw.contentHash())) {
            return false;
        }
        notifications.save(CapturedNotification.receive(NotificationId.newId(), owner, raw, clock.instant()));
        return true;
    }
}
