package com.personaltracker.capture.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CapturedNotificationTests {

    private static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");

    private CapturedNotification received() {
        return CapturedNotification.receive(NotificationId.newId(), UserId.newId(),
                new RawNotification("Mensajes", "85540", "Bancolombia: …", NOW), NOW);
    }

    @Test
    void startsPendingAndCanOnlyBeProcessedOnce() {
        CapturedNotification notification = received();
        assertThat(notification.isPending()).isTrue();

        notification.markDiscarded(NOW);

        assertThat(notification.status()).isEqualTo(CaptureStatus.DISCARDED);
        assertThatThrownBy(() -> notification.markDiscarded(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reviewReasonsWithMovementRequireTheTransactionAndTheOthersRejectIt() {
        assertThatThrownBy(() -> received().markForReview(ReviewReason.POSSIBLE_OWN_TRANSFER, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> received().markForReview(ReviewReason.UNRECOGNIZED_TEXT, UUID.randomUUID(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        CapturedNotification notification = received();
        notification.markForReview(ReviewReason.MISSING_EXCHANGE_RATE, null, null, NOW);
        assertThat(notification.reviewReason()).contains(ReviewReason.MISSING_EXCHANGE_RATE);
    }
}
