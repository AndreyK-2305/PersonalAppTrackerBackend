package com.personaltracker.capture.domain.model;

import com.personaltracker.shared.domain.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Movimiento que {@code capture} pide registrar a {@code finance}, siempre en COP y sin categoría
 * (la categoría es manual). Un gasto solo tiene origen, un ingreso solo destino y una transferencia
 * ambos.
 *
 * @param estimatedAmount el monto es una conversión con la TRM y no el valor final del banco
 * @param description nota visible, por ejemplo el monto original en dólares y la tasa usada
 */
public record CaptureEntry(
        EntryType type,
        Optional<UUID> source,
        Optional<UUID> destination,
        Money amount,
        LocalDate date,
        String merchant,
        CaptureChannel channel,
        boolean estimatedAmount,
        Optional<String> description) {

    public enum EntryType {
        EXPENSE,
        INCOME,
        TRANSFER
    }

    public CaptureEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(merchant, "merchant");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(description, "description");
        boolean valid = switch (type) {
            case EXPENSE -> source.isPresent() && destination.isEmpty();
            case INCOME -> source.isEmpty() && destination.isPresent();
            case TRANSFER -> source.isPresent() && destination.isPresent() && !source.equals(destination);
        };
        if (!valid) {
            throw new IllegalArgumentException("Productos inválidos para un movimiento de tipo " + type);
        }
    }
}
