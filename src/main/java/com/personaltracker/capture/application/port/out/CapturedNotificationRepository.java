package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Cola y registro de notificaciones capturadas. */
public interface CapturedNotificationRepository {

    Optional<CapturedNotification> findById(NotificationId id);

    void save(CapturedNotification notification);

    /**
     * Notificaciones del usuario que registraron un movimiento ({@code RECORDED} o {@code REVIEW} con
     * movimiento), cuya huella ocurrió entre {@code from} y {@code to}, y con las que todavía no se
     * emparejó ninguna otra notificación. Candidatas a ser la misma compra que una nueva.
     */
    List<CapturedNotification> findUnpairedWithMovement(UserId owner, Instant from, Instant to);

    /** Atajo para la ventana de una huella. */
    default List<CapturedNotification> findUnpairedWithMovement(UserId owner, CaptureFootprint footprint) {
        return findUnpairedWithMovement(owner, footprint.windowStart(), footprint.windowEnd());
    }
}
