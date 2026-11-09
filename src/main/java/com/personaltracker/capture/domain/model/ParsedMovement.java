package com.personaltracker.capture.domain.model;

import com.personaltracker.shared.domain.Money;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Movimiento extraído de una notificación, todavía sin producto ni usuario resueltos.
 *
 * @param source fuente de la notificación, para emparejar avisos de la misma compra
 * @param templateId plantilla que lo reconoció (por ejemplo, {@code BANCOLOMBIA_CARD_PURCHASE})
 * @param amount monto en la moneda del texto: COP, o USD en compras en el exterior
 * @param occurredAt fecha y hora local del texto; vacía si el texto no la trae (se usa la hora de
 *     recepción)
 * @param counterparty comercio, remitente o destinatario tal como aparece en el texto
 * @param destinationKey llave Bre-B de destino, en transferencias enviadas a una llave
 * @param recipientName nombre del destinatario, para reconocer transferencias a uno mismo
 */
public record ParsedMovement(
        CaptureSource source,
        String templateId,
        MovementDirection direction,
        ProductHint product,
        Money amount,
        Optional<LocalDateTime> occurredAt,
        String counterparty,
        CaptureChannel channel,
        Optional<String> destinationKey,
        Optional<String> recipientName) {

    public ParsedMovement {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(destinationKey, "destinationKey");
        Objects.requireNonNull(recipientName, "recipientName");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El monto de una notificación debe ser positivo");
        }
    }
}
