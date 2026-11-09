package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra el texto crudo de las notificaciones con más de {@link #RETENTION} (decisión del usuario,
 * ADR-0017). Se conservan los datos extraídos y el movimiento.
 */
@Service
public class PurgeRawNotifications {

    public static final Duration RETENTION = Duration.ofDays(45);

    private final CapturedNotificationRepository notifications;
    private final Clock clock;

    public PurgeRawNotifications(CapturedNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Devuelve cuántas notificaciones limpió. */
    @Transactional
    public int execute() {
        Instant now = clock.instant();
        return notifications.purgeRawText(now.minus(RETENTION), now);
    }
}
