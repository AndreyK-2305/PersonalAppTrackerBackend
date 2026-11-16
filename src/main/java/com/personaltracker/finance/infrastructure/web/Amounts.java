package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.domain.model.SupportedCurrencies;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.infrastructure.web.ApiFormats;
import java.math.BigDecimal;

/**
 * Montos en la API: cadenas decimales con hasta 2 decimales, nunca números JSON (ADR-0006). Las
 * peticiones no indican la moneda porque el MVP solo admite COP; las respuestas la incluyen.
 */
final class Amounts {

    /** Monto no negativo, por ejemplo {@code "150000.50"}. */
    static final String UNSIGNED = ApiFormats.AMOUNT;

    /** Monto con signo opcional, para saldos iniciales que admiten sobregiro. */
    static final String SIGNED = ApiFormats.SIGNED_AMOUNT;

    static final String MESSAGE = ApiFormats.AMOUNT_MESSAGE;

    private Amounts() {
    }

    /** Convierte un texto ya validado con {@link #UNSIGNED} o {@link #SIGNED}. */
    static Money cop(String value) {
        return new Money(new BigDecimal(value), SupportedCurrencies.COP);
    }

    /** Usa {@code fallback} si el monto no viene en la petición. */
    static Money copOr(String value, String fallback) {
        return cop(value == null || value.isBlank() ? fallback : value);
    }

    static String format(Money money) {
        return money == null ? null : money.amount().toPlainString();
    }
}
