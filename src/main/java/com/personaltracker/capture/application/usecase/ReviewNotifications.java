package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.application.port.out.Ledger;
import com.personaltracker.capture.domain.exception.NotificationNotFoundException;
import com.personaltracker.capture.domain.exception.RawTextExpiredException;
import com.personaltracker.capture.domain.exception.ReviewActionNotAllowedException;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja "Por revisar" y sus acciones (ADR-0017). Cada acción corre en una transacción con el
 * cambio en {@code finance}: si una falla, no queda nada a medias. Una notificación de otro usuario
 * responde 404 y una acción que no corresponde a su motivo, 422.
 */
@Service
public class ReviewNotifications {

    private final CapturedNotificationRepository notifications;
    private final Ledger ledger;
    private final ProcessNotification processNotification;
    private final Clock clock;

    public ReviewNotifications(
            CapturedNotificationRepository notifications,
            Ledger ledger,
            ProcessNotification processNotification,
            Clock clock) {
        this.notifications = notifications;
        this.ledger = ledger;
        this.processNotification = processNotification;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CapturedNotification> list(UserId owner) {
        return notifications.findInReview(owner);
    }

    /** Acepta el movimiento como quedó: un gasto que "No es mía", o un movimiento en "Otro". */
    @Transactional
    public void confirm(UserId owner, NotificationId id) {
        CapturedNotification notification = require(owner, id);
        notification.confirm(clock.instant());
        notifications.save(notification);
    }

    /** "Es mía": el gasto pasa a ser una transferencia hacia {@code destination}. */
    @Transactional
    public void markAsOwnTransfer(UserId owner, NotificationId id, UUID destination) {
        CapturedNotification notification = require(owner, id);
        if (notification.reviewReason().filter(ReviewReason.POSSIBLE_OWN_TRANSFER::equals).isEmpty()) {
            throw new ReviewActionNotAllowedException();
        }
        UUID transfer = ledger.convertExpenseToTransfer(owner, notification.transactionId().orElseThrow(), destination);
        notification.resolveWithMovement(ReviewReason.POSSIBLE_OWN_TRANSFER, transfer, clock.instant());
        notifications.save(notification);
    }

    /** Registra una compra en dólares sin TRM con el valor en pesos del extracto. */
    @Transactional
    public void setManualAmount(UserId owner, NotificationId id, Money copAmount) {
        CapturedNotification notification = require(owner, id);
        if (notification.raw().body().isEmpty()) {
            throw new RawTextExpiredException();
        }
        notification.reopenForManualAmount(clock.instant());
        processNotification.processWithManualAmount(notification, copAmount);
    }

    /** Descarta la notificación y borra su movimiento, si lo tiene: no era un movimiento real. */
    @Transactional
    public void discard(UserId owner, NotificationId id) {
        CapturedNotification notification = require(owner, id);
        if (notification.status() != CaptureStatus.REVIEW) {
            throw new ReviewActionNotAllowedException();
        }
        notification.transactionId().ifPresent(transaction -> ledger.delete(owner, transaction));
        notification.discard(clock.instant());
        notifications.save(notification);
    }

    /**
     * "No es mía" sobre una transferencia captada por llave propia: pasa a ser un gasto. 404 si el
     * movimiento no viene de una notificación del usuario.
     */
    @Transactional
    public void markTransferAsNotOwn(UserId owner, UUID transactionId) {
        CapturedNotification notification = notifications.findByTransaction(owner, transactionId)
                .orElseThrow(NotificationNotFoundException::new);
        UUID expense = ledger.convertTransferToExpense(owner, transactionId);
        notification.replaceMovement(expense, clock.instant());
        notifications.save(notification);
    }

    private CapturedNotification require(UserId owner, NotificationId id) {
        return notifications.findByIdAndOwner(id, owner).orElseThrow(NotificationNotFoundException::new);
    }
}
