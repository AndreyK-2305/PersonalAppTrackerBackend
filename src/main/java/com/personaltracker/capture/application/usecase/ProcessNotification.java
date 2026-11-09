package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CaptureOwners;
import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.application.port.out.ExchangeRates;
import com.personaltracker.capture.application.port.out.Ledger;
import com.personaltracker.capture.application.port.out.OwnKeys;
import com.personaltracker.capture.application.port.out.UserProducts;
import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.capture.domain.model.CaptureEntry.EntryType;
import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.OwnedProduct;
import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ParsedMovement;
import com.personaltracker.capture.domain.model.ProductResolution;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.capture.domain.parsing.NotificationParsers;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procesa una notificación de la cola (ADR-0017), una vez pasada la espera de
 * {@link CapturedNotification#SETTLE_DELAY}:
 *
 * <ol>
 *   <li>La analiza, identifica el producto y convierte los dólares con la TRM.
 *   <li>Si otra fuente ya registró la misma compra (mismo producto y monto en 5 minutos), la
 *       empareja con ella y no registra nada.
 *   <li>Si no, decide el tipo de movimiento y lo registra en {@code finance}, o lo deja "Por revisar".
 * </ol>
 *
 * <p>Con varios motivos de revisión gana el producto ambiguo sobre la posible transferencia propia.
 * La cola procesa primero los avisos del banco y después los de Wallet, para que el movimiento
 * registrado tenga los datos más completos.
 */
@Service
public class ProcessNotification {

    static final Currency COP = Currency.getInstance("COP");
    static final Currency USD = Currency.getInstance("USD");
    static final int MAX_MERCHANT_LENGTH = 100;

    private final CapturedNotificationRepository notifications;
    private final NotificationParsers parsers;
    private final UserProducts products;
    private final CaptureOwners owners;
    private final OwnKeys ownKeys;
    private final ExchangeRates exchangeRates;
    private final Ledger ledger;
    private final Clock clock;

    public ProcessNotification(
            CapturedNotificationRepository notifications,
            NotificationParsers parsers,
            UserProducts products,
            CaptureOwners owners,
            OwnKeys ownKeys,
            ExchangeRates exchangeRates,
            Ledger ledger,
            Clock clock) {
        this.notifications = notifications;
        this.parsers = parsers;
        this.products = products;
        this.owners = owners;
        this.ownKeys = ownKeys;
        this.exchangeRates = exchangeRates;
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * No hace nada si la notificación no existe, ya fue procesada, la está procesando otra instancia
     * o todavía no pasó la espera: la cola puede reintentar.
     */
    @Transactional
    public void execute(NotificationId id) {
        Instant now = clock.instant();
        Optional<CapturedNotification> found = notifications.lockPending(id)
                .filter(pending -> pending.isSettledAt(now));
        if (found.isEmpty()) {
            return;
        }
        process(found.get(), now, Optional.empty());
    }

    /**
     * Procesa una compra en dólares que quedó sin TRM con el valor en pesos que escribió el usuario
     * (ya reabierta con {@code reopenForManualAmount}). El monto no queda como estimado, porque es el
     * valor real. Recorre el mismo flujo: producto, emparejamiento y tipo de movimiento.
     */
    @Transactional
    public void processWithManualAmount(CapturedNotification notification, Money copAmount) {
        if (!notification.isPending() || !copAmount.currency().equals(COP) || !copAmount.isPositive()) {
            throw new IllegalArgumentException("Se esperaba una notificación reabierta y un monto positivo en COP");
        }
        process(notification, clock.instant(), Optional.of(copAmount));
    }

    private void process(CapturedNotification notification, Instant now, Optional<Money> manualAmount) {
        switch (parsers.parse(notification.raw())) {
            case ParseOutcome.NotAMovement ignored -> notification.markDiscarded(now);
            case ParseOutcome.Unrecognized ignored ->
                    notification.markForReview(ReviewReason.UNRECOGNIZED_TEXT, null, null, now);
            case ParseOutcome.Recognized recognized ->
                    record(notification, recognized.movement(), now, manualAmount);
        }
        notifications.save(notification);
    }

    private void record(
            CapturedNotification notification, ParsedMovement movement, Instant now, Optional<Money> manualAmount) {
        UserId owner = notification.owner();
        ZoneId zone = owners.timeZone(owner);
        Instant occurredAt = movement.occurredAt()
                .map(local -> local.atZone(zone).toInstant())
                .orElse(notification.raw().receivedAt());
        LocalDate date = occurredAt.atZone(zone).toLocalDate();

        Optional<Converted> converted = manualAmount.isPresent()
                ? manualAmount.map(value -> new Converted(value, Optional.empty()))
                : toCop(movement.amount(), date);
        if (converted.isEmpty()) {
            notification.markForReview(ReviewReason.MISSING_EXCHANGE_RATE, null, null, now);
            return;
        }
        Optional<Money> amount = converted.map(Converted::amount);
        Optional<BigDecimal> rate = converted.get().rate();

        List<OwnedProduct> active = products.activeProducts(owner);
        ProductResolution resolution = ProductResolution.resolve(movement.product(), active);
        UUID product = resolution instanceof ProductResolution.Matched matched
                ? matched.productId()
                : products.otherProduct(owner);
        ReviewReason reason = resolution instanceof ProductResolution.Ambiguous ? ReviewReason.AMBIGUOUS_PRODUCT : null;

        CaptureFootprint footprint = new CaptureFootprint(product, amount.get(), movement.source(), occurredAt);
        // Solo se empareja sobre un producto identificado: "Otro" agrupa productos distintos.
        if (resolution instanceof ProductResolution.Matched) {
            Optional<CapturedNotification> counterpart = samePurchase(notification, footprint);
            if (counterpart.isPresent()) {
                notification.markPaired(counterpart.get(), footprint, now);
                return;
            }
        }

        EntryType type;
        Optional<UUID> source = Optional.empty();
        Optional<UUID> destination = Optional.empty();
        if (movement.direction() == MovementDirection.INFLOW) {
            type = EntryType.INCOME;
            destination = Optional.of(product);
        } else {
            source = Optional.of(product);
            destination = resolution instanceof ProductResolution.Matched
                    ? movement.destinationKey()
                            .flatMap(key -> ownKeys.productFor(owner, key))
                            .filter(keyProduct -> !keyProduct.equals(product))
                            // Una llave de un producto borrado o archivado se ignora.
                            .filter(keyProduct -> active.stream().anyMatch(owned -> owned.id().equals(keyProduct)))
                    : Optional.empty();
            type = destination.isPresent() ? EntryType.TRANSFER : EntryType.EXPENSE;
            if (type == EntryType.EXPENSE && reason == null && isOwnName(owner, movement)) {
                reason = ReviewReason.POSSIBLE_OWN_TRANSFER;
            }
        }

        CaptureEntry entry = new CaptureEntry(type, source, destination, amount.get(), date,
                truncate(movement.counterparty()), movement.channel(), rate.isPresent(),
                rate.map(value -> conversionNote(movement.amount(), value)));
        UUID transactionId = ledger.record(owner, entry);
        if (reason == null) {
            notification.markRecorded(transactionId, footprint, now);
        } else {
            notification.markForReview(reason, transactionId, footprint, now);
        }
    }

    /** El aviso de otra fuente, sin emparejar, que registró la misma compra; el más cercano en el tiempo. */
    private Optional<CapturedNotification> samePurchase(CapturedNotification notification, CaptureFootprint footprint) {
        return notifications.findUnpairedWithMovement(notification.owner(), footprint).stream()
                .filter(candidate -> !candidate.id().equals(notification.id()))
                .filter(candidate -> candidate.footprint().filter(footprint::isSamePurchaseAs).isPresent())
                .min(Comparator.comparing(candidate -> candidate.footprint().orElseThrow().distanceTo(footprint)));
    }

    private boolean isOwnName(UserId owner, ParsedMovement movement) {
        return movement.recipientName().filter(name -> owners.name(owner).matches(name)).isPresent();
    }

    /** Monto en COP y, si hubo conversión, la tasa usada. */
    private record Converted(Money amount, Optional<BigDecimal> rate) {}

    /** El monto en COP: igual si ya está en COP, o convertido con la TRM si está en dólares. */
    private Optional<Converted> toCop(Money amount, LocalDate date) {
        if (amount.currency().equals(COP)) {
            return Optional.of(new Converted(amount, Optional.empty()));
        }
        if (!amount.currency().equals(USD)) {
            return Optional.empty();
        }
        return exchangeRates.copPerUsd(date)
                .filter(rate -> rate.signum() > 0)
                .map(rate -> new Converted(
                        new Money(amount.amount().multiply(rate).setScale(Money.SCALE, RoundingMode.HALF_EVEN), COP),
                        Optional.of(rate)));
    }

    private static String conversionNote(Money original, BigDecimal rate) {
        return "Compra en " + original.currency().getCurrencyCode() + " " + original.amount().toPlainString()
                + " convertida con la TRM del día (" + rate.toPlainString() + "). Monto estimado.";
    }

    private static String truncate(String merchant) {
        return merchant.length() <= MAX_MERCHANT_LENGTH ? merchant : merchant.substring(0, MAX_MERCHANT_LENGTH);
    }
}
