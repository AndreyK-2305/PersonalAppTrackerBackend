package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.domain.model.SupportedCurrencies;
import com.personaltracker.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Montos en la API: cadenas decimales con hasta 2 decimales, nunca números JSON (ADR-0006). Las
 * peticiones no indican la moneda porque el MVP solo admite COP; las respuestas la incluyen.
 */
final class Amounts {

    /** Monto no negativo, por ejemplo {@code "150000.50"}. */
    static final String UNSIGNED = "^[0-9]{1,17}(\\.[0-9]{1,2})?$";

    /** Monto con signo opcional, para saldos iniciales que admiten sobregiro. */
    static final String SIGNED = "^-?[0-9]{1,17}(\\.[0-9]{1,2})?$";

    static final String MESSAGE = "debe ser un monto con hasta 2 decimales, por ejemplo 150000.50";

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
