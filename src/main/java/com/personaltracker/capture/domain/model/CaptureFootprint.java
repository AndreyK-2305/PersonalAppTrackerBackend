package com.personaltracker.capture.domain.model;

import com.personaltracker.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que identifica una compra para reconocer avisos repetidos de fuentes distintas (ADR-0017).
 *
 * <p>Dos huellas son la misma compra si vienen de <b>fuentes distintas</b> (por ejemplo, el SMS del
 * banco y Wallet), tienen el mismo producto y el mismo monto en COP, y ocurrieron con menos de
 * {@link #PAIRING_WINDOW} de diferencia. El comercio no se compara: Wallet usa la ubicación y el
 * banco el nombre registrado por el comercio. El monto admite menos de un peso de diferencia, porque
 * Wallet lo muestra sin centavos.
 *
 * <p>Dos huellas de la misma fuente nunca se emparejan: tres compras iguales seguidas son tres SMS y
 * tres avisos de Wallet, y se emparejan de a uno.
 */
public record CaptureFootprint(UUID productId, Money amount, CaptureSource source, Instant occurredAt) {

    public static final Duration PAIRING_WINDOW = Duration.ofMinutes(5);

    private static final BigDecimal AMOUNT_TOLERANCE = BigDecimal.ONE;

    public CaptureFootprint {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public boolean isSamePurchaseAs(CaptureFootprint other) {
        return source != other.source
                && productId.equals(other.productId)
                && amount.currency().equals(other.amount.currency())
                && amount.amount().subtract(other.amount.amount()).abs().compareTo(AMOUNT_TOLERANCE) < 0
                && Duration.between(occurredAt, other.occurredAt).abs().compareTo(PAIRING_WINDOW) <= 0;
    }

    /** Distancia en el tiempo, para elegir el aviso más cercano cuando hay varios candidatos. */
    public Duration distanceTo(CaptureFootprint other) {
        return Duration.between(occurredAt, other.occurredAt).abs();
    }

    public Instant windowStart() {
        return occurredAt.minus(PAIRING_WINDOW);
    }

    public Instant windowEnd() {
        return occurredAt.plus(PAIRING_WINDOW);
    }
}
