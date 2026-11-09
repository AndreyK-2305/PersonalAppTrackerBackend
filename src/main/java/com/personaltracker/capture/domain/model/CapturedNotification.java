package com.personaltracker.capture.domain.model;

import com.personaltracker.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Notificación recibida de un dispositivo del usuario y lo que se hizo con ella (ADR-0017). Conserva
 * el identificador del movimiento que generó, para poder descartarlo o convertirlo desde la revisión,
 * o la notificación con la que se emparejó si repetía una compra ya registrada.
 */
public final class CapturedNotification {

    /**
     * Espera desde la recepción antes de analizarla, para que lleguen los demás avisos de la misma
     * compra (SMS, app del banco, Wallet) y se emparejen en lugar de registrarse dos veces.
     */
    public static final Duration SETTLE_DELAY = Duration.ofMinutes(2);

    private final NotificationId id;
    private final UserId owner;
    private final RawNotification raw;
    private final Instant createdAt;
    private CaptureStatus status;
    private ReviewReason reviewReason;
    private UUID transactionId;
    private NotificationId pairedWith;
    private CaptureFootprint footprint;
    private Instant updatedAt;

    private CapturedNotification(
            NotificationId id,
            UserId owner,
            RawNotification raw,
            CaptureStatus status,
            ReviewReason reviewReason,
            UUID transactionId,
            NotificationId pairedWith,
            CaptureFootprint footprint,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.raw = Objects.requireNonNull(raw, "raw");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewReason = reviewReason;
        this.transactionId = transactionId;
        this.pairedWith = pairedWith;
        this.footprint = footprint;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CapturedNotification receive(NotificationId id, UserId owner, RawNotification raw, Instant now) {
        return new CapturedNotification(id, owner, raw, CaptureStatus.RECEIVED, null, null, null, null, now, now);
    }

    /** Reconstruye una notificación guardada. */
    public static CapturedNotification restore(
            NotificationId id,
            UserId owner,
            RawNotification raw,
            CaptureStatus status,
            ReviewReason reviewReason,
            UUID transactionId,
            NotificationId pairedWith,
            CaptureFootprint footprint,
            Instant createdAt,
            Instant updatedAt) {
        return new CapturedNotification(
                id, owner, raw, status, reviewReason, transactionId, pairedWith, footprint, createdAt, updatedAt);
    }

    public boolean isPending() {
        return status == CaptureStatus.RECEIVED;
    }

    /** Si ya pasó la espera desde que el servidor la recibió. */
    public boolean isSettledAt(Instant now) {
        return !createdAt.plus(SETTLE_DELAY).isAfter(now);
    }

    /** Si generó un movimiento que otra fuente puede confirmar. */
    public boolean hasMovement() {
        return (status == CaptureStatus.RECORDED || status == CaptureStatus.REVIEW) && transactionId != null;
    }

    public void markRecorded(UUID transactionId, CaptureFootprint footprint, Instant now) {
        requirePending();
        this.status = CaptureStatus.RECORDED;
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.footprint = footprint;
        this.updatedAt = now;
    }

    /**
     * Pasa a revisión. Los motivos que dejan un movimiento registrado exigen su identificador; los
     * demás no lo admiten.
     */
    public void markForReview(ReviewReason reason, UUID transactionId, CaptureFootprint footprint, Instant now) {
        requirePending();
        Objects.requireNonNull(reason, "reason");
        if (reason.hasMovement() != (transactionId != null)) {
            throw new IllegalArgumentException("El motivo " + reason + " no corresponde al movimiento recibido");
        }
        this.status = CaptureStatus.REVIEW;
        this.reviewReason = reason;
        this.transactionId = transactionId;
        this.footprint = footprint;
        this.updatedAt = now;
    }

    /** Repite la compra que registró {@code counterpart}, de otra fuente: no genera movimiento. */
    public void markPaired(CapturedNotification counterpart, CaptureFootprint footprint, Instant now) {
        requirePending();
        if (!counterpart.hasMovement() || !counterpart.owner.equals(owner)) {
            throw new IllegalArgumentException("Solo se empareja con un movimiento registrado del mismo usuario");
        }
        this.status = CaptureStatus.PAIRED;
        this.pairedWith = counterpart.id;
        this.footprint = Objects.requireNonNull(footprint, "footprint");
        this.updatedAt = now;
    }

    public void markDiscarded(Instant now) {
        requirePending();
        this.status = CaptureStatus.DISCARDED;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("La notificación " + id.value() + " ya fue procesada");
        }
    }

    public NotificationId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public RawNotification raw() {
        return raw;
    }

    public CaptureStatus status() {
        return status;
    }

    public Optional<ReviewReason> reviewReason() {
        return Optional.ofNullable(reviewReason);
    }

    public Optional<UUID> transactionId() {
        return Optional.ofNullable(transactionId);
    }

    public Optional<NotificationId> pairedWith() {
        return Optional.ofNullable(pairedWith);
    }

    public Optional<CaptureFootprint> footprint() {
        return Optional.ofNullable(footprint);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
