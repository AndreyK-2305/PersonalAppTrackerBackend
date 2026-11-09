package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Trabajo de la cola (ADR-0017): procesa las notificaciones que ya pasaron la espera, primero las de
 * los bancos y después las de Wallet. Cada una se procesa en su propia transacción: si una falla,
 * queda pendiente para el siguiente ciclo y las demás siguen. Los registros solo llevan el
 * identificador, nunca el texto.
 */
@Service
public class ProcessPendingNotifications {

    static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(ProcessPendingNotifications.class);

    private final CapturedNotificationRepository notifications;
    private final ProcessNotification processNotification;
    private final Clock clock;

    public ProcessPendingNotifications(
            CapturedNotificationRepository notifications, ProcessNotification processNotification, Clock clock) {
        this.notifications = notifications;
        this.processNotification = processNotification;
        this.clock = clock;
    }

    /** Devuelve cuántas notificaciones intentó procesar. */
    public int execute() {
        List<NotificationId> ready = notifications.findReadyToProcess(
                clock.instant().minus(CapturedNotification.SETTLE_DELAY), BATCH_SIZE);
        for (NotificationId id : ready) {
            try {
                processNotification.execute(id);
            } catch (RuntimeException failure) {
                log.warn("No fue posible procesar la notificación {}: {}", id.value(),
                        failure.getClass().getSimpleName());
            }
        }
        return ready.size();
    }
}
