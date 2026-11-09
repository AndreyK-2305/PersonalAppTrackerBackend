package com.personaltracker.finance.api;

import com.personaltracker.shared.domain.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Movimiento captado. Un gasto solo lleva {@code source}, un ingreso solo {@code destination} y una
 * transferencia ambos.
 *
 * @param merchant comercio o contraparte, hasta 100 caracteres
 * @param amountEstimated el monto es una conversión con la TRM y no el valor final del banco
 * @param description nota opcional, hasta 200 caracteres
 */
public record CapturedTransaction(
        Kind kind,
        UUID source,
        UUID destination,
        Money amount,
        LocalDate date,
        String merchant,
        Channel channel,
        boolean amountEstimated,
        String description) {

    public enum Kind {
        EXPENSE,
        INCOME,
        TRANSFER
    }

    public enum Channel {
        CARD,
        BANK_TRANSFER,
        DEPOSIT
    }

    public CapturedTransaction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(channel, "channel");
    }
}
