package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cola y registro de notificaciones capturadas. */
public interface CapturedNotificationRepository {

    Optional<CapturedNotification> findById(NotificationId id);

    /** Un recurso ajeno se trata igual que uno inexistente. */
    Optional<CapturedNotification> findByIdAndOwner(NotificationId id, UserId owner);

    /** La notificación que generó el movimiento, si la hay. */
    Optional<CapturedNotification> findByTransaction(UserId owner, UUID transactionId);

    /** Las notificaciones "Por revisar" del usuario, las más recientes primero. */
    List<CapturedNotification> findInReview(UserId owner);

    /**
     * La notificación si sigue pendiente, bloqueada hasta el final de la transacción. Vacío si no
     * existe, ya se procesó o la está procesando otra instancia ({@code FOR UPDATE SKIP LOCKED}).
     */
    Optional<CapturedNotification> lockPending(NotificationId id);

    void save(CapturedNotification notification);

    /** Si el usuario ya envió una notificación con el mismo contenido (reenvío exacto del atajo). */
    boolean existsByContent(UserId owner, String contentHash);

    /**
     * Pendientes recibidas antes de {@code receivedBefore}, como máximo {@code limit}. Primero las de
     * los bancos y después las de Wallet, que traen menos datos; dentro de cada grupo, por llegada.
     */
    List<NotificationId> findReadyToProcess(Instant receivedBefore, int limit);

    /**
     * Notificaciones del usuario que registraron un movimiento ({@code RECORDED} o {@code REVIEW} con
     * movimiento), cuya huella ocurrió entre {@code from} y {@code to}, y con las que todavía no se
     * emparejó ninguna otra. Candidatas a ser la misma compra que una nueva.
     */
    List<CapturedNotification> findUnpairedWithMovement(UserId owner, Instant from, Instant to);

    /** Atajo para la ventana de una huella. */
    default List<CapturedNotification> findUnpairedWithMovement(UserId owner, CaptureFootprint footprint) {
        return findUnpairedWithMovement(owner, footprint.windowStart(), footprint.windowEnd());
    }

    /**
     * Borra el título y el texto de las notificaciones recibidas antes de {@code receivedBefore}
     * (ADR-0017: 45 días) y devuelve cuántas limpió. Los datos extraídos se conservan.
     */
    int purgeRawText(Instant receivedBefore, Instant now);
}
