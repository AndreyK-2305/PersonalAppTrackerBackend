package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El usuario borró en Finanzas un movimiento captado: su notificación queda descartada, se conserva
 * y no se vuelve a procesar (ADR-0015).
 */
@Service
public class DiscardDeletedTransaction {

    private final CapturedNotificationRepository notifications;
    private final Clock clock;

    public DiscardDeletedTransaction(CapturedNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void execute(UserId owner, UUID transactionId) {
        notifications.findByTransaction(owner, transactionId)
                .filter(notification -> notification.status() == CaptureStatus.RECORDED
                        || notification.status() == CaptureStatus.REVIEW)
                .ifPresent(notification -> {
                    notification.discard(clock.instant());
                    notifications.save(notification);
                });
    }
}
