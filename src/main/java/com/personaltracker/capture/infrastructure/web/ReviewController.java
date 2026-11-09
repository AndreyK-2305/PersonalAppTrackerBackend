package com.personaltracker.capture.infrastructure.web;

import com.personaltracker.capture.application.usecase.ReviewNotifications;
import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja "Por revisar" de la captura y sus acciones (ADR-0017). En la interfaz se muestra como un
 * filtro de Movimientos, no como una sección aparte.
 */
@RestController
@RequestMapping("/api/v1/capture")
class ReviewController {

    private static final Currency COP = Currency.getInstance("COP");

    private final ReviewNotifications reviews;

    ReviewController(ReviewNotifications reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/reviews")
    List<ReviewResponse> list(@CurrentUser UserId owner) {
        return reviews.list(owner).stream().map(ReviewResponse::from).toList();
    }

    /** Acepta el movimiento como quedó registrado. Aplica a los motivos que dejan un movimiento. */
    @PostMapping("/reviews/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@CurrentUser UserId owner, @PathVariable UUID id) {
        reviews.confirm(owner, new NotificationId(id));
    }

    /** "Es mía": el gasto pasa a ser una transferencia hacia una cuenta propia. */
    @PostMapping("/reviews/{id}/own-transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ownTransfer(@CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody OwnTransferRequest body) {
        reviews.markAsOwnTransfer(owner, new NotificationId(id), body.destinationProductId());
    }

    /** Valor en pesos de una compra en dólares que quedó sin TRM. */
    @PostMapping("/reviews/{id}/amount")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void manualAmount(@CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody AmountRequest body) {
        reviews.setManualAmount(owner, new NotificationId(id), new Money(new BigDecimal(body.amount()), COP));
    }

    /** No era un movimiento: descarta la notificación y borra su movimiento, si lo tiene. */
    @PostMapping("/reviews/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@CurrentUser UserId owner, @PathVariable UUID id) {
        reviews.discard(owner, new NotificationId(id));
    }

    /** "No es mía" sobre una transferencia captada por una llave propia: pasa a ser un gasto. */
    @PostMapping("/transactions/{transactionId}/not-own")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void notOwn(@CurrentUser UserId owner, @PathVariable UUID transactionId) {
        reviews.markTransferAsNotOwn(owner, transactionId);
    }

    record OwnTransferRequest(@NotNull UUID destinationProductId) {}

    /** Monto en COP como cadena decimal con punto y hasta 2 decimales, mayor que cero. */
    record AmountRequest(
            @NotNull @Pattern(regexp = "^(?!0+(\\.0{1,2})?$)[0-9]{1,17}(\\.[0-9]{1,2})?$") String amount) {}

    /**
     * Notificación por revisar. {@code title} y {@code body} quedan vacíos cuando el texto ya se borró
     * (45 días). {@code transactionId}, {@code amount} y {@code productId} son nulos si el motivo no
     * dejó un movimiento ({@code UNRECOGNIZED_TEXT} o {@code MISSING_EXCHANGE_RATE}).
     */
    @Schema(requiredProperties = {"id", "reason", "app", "title", "body", "receivedAt"})
    record ReviewResponse(
            UUID id,
            ReviewReason reason,
            String app,
            String title,
            String body,
            Instant receivedAt,
            UUID transactionId,
            String amount,
            String currency,
            UUID productId) {

        static ReviewResponse from(CapturedNotification notification) {
            Optional<CaptureFootprint> footprint = notification.footprint();
            return new ReviewResponse(
                    notification.id().value(),
                    notification.reviewReason().orElseThrow(),
                    notification.raw().app(),
                    notification.raw().title(),
                    notification.raw().body(),
                    notification.raw().receivedAt(),
                    notification.transactionId().orElse(null),
                    footprint.map(value -> value.amount().amount().toPlainString()).orElse(null),
                    footprint.map(value -> value.amount().currency().getCurrencyCode()).orElse(null),
                    footprint.map(CaptureFootprint::productId).orElse(null));
        }
    }
}
