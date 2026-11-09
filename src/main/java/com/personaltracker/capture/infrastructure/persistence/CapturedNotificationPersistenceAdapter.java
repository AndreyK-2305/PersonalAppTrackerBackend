package com.personaltracker.capture.infrastructure.persistence;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CaptureSource;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Adaptador de la cola de notificaciones sobre {@code capture.notifications}. */
@Component
class CapturedNotificationPersistenceAdapter implements CapturedNotificationRepository {

    private final CapturedNotificationJpaRepository repository;

    CapturedNotificationPersistenceAdapter(CapturedNotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CapturedNotification> findById(NotificationId id) {
        return repository.findById(id.value()).map(CapturedNotificationPersistenceAdapter::toDomain);
    }

    /** El texto crudo solo se escribe al crear la fila: después solo cambia el resultado del análisis. */
    @Override
    public void save(CapturedNotification notification) {
        CapturedNotificationJpaEntity entity = repository.findById(notification.id().value()).orElseGet(() -> {
            CapturedNotificationJpaEntity created = new CapturedNotificationJpaEntity(notification.id().value());
            RawNotification raw = notification.raw();
            created.setUserId(notification.owner().value());
            created.setApp(raw.app());
            created.setTitle(raw.title());
            created.setBody(raw.body());
            created.setReceivedAt(raw.receivedAt());
            created.setContentHash(raw.contentHash());
            created.setCreatedAt(notification.createdAt());
            return created;
        });
        entity.setStatus(notification.status().name());
        entity.setReviewReason(notification.reviewReason().map(Enum::name).orElse(null));
        entity.setTransactionId(notification.transactionId().orElse(null));
        entity.setPairedWith(notification.pairedWith().map(NotificationId::value).orElse(null));
        Optional<CaptureFootprint> footprint = notification.footprint();
        entity.setProductId(footprint.map(CaptureFootprint::productId).orElse(null));
        entity.setAmount(footprint.map(value -> value.amount().amount()).orElse(null));
        entity.setCurrency(footprint.map(value -> value.amount().currency().getCurrencyCode()).orElse(null));
        entity.setSource(footprint.map(value -> value.source().name()).orElse(null));
        entity.setOccurredAt(footprint.map(CaptureFootprint::occurredAt).orElse(null));
        entity.setUpdatedAt(notification.updatedAt());
        repository.save(entity);
    }

    @Override
    public List<CapturedNotification> findUnpairedWithMovement(UserId owner, Instant from, Instant to) {
        return repository.findUnpairedWithMovement(owner.value(), from, to).stream()
                .map(CapturedNotificationPersistenceAdapter::toDomain)
                .toList();
    }

    private static CapturedNotification toDomain(CapturedNotificationJpaEntity entity) {
        RawNotification raw = new RawNotification(
                entity.getApp(), entity.getTitle(), entity.getBody(), entity.getReceivedAt());
        CaptureFootprint footprint = entity.getProductId() == null ? null : new CaptureFootprint(
                entity.getProductId(),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                CaptureSource.valueOf(entity.getSource()),
                entity.getOccurredAt());
        return CapturedNotification.restore(
                new NotificationId(entity.getId()),
                new UserId(entity.getUserId()),
                raw,
                CaptureStatus.valueOf(entity.getStatus()),
                entity.getReviewReason() == null ? null : ReviewReason.valueOf(entity.getReviewReason()),
                entity.getTransactionId(),
                entity.getPairedWith() == null ? null : new NotificationId(entity.getPairedWith()),
                footprint,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
