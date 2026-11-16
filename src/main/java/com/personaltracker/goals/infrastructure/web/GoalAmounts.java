package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * Montos y tasas de Metas en la API: cadenas decimales en COP y porcentajes EA (ADR-0006 y ADR-0018).
 * Las peticiones no indican la moneda; las respuestas la incluyen.
 */
final class GoalAmounts {

    static final Currency COP = Currency.getInstance("COP");

    private GoalAmounts() {
    }

    /** Convierte un texto ya validado con el patrón de montos. */
    static Money cop(String value) {
        return new Money(new BigDecimal(value), COP);
    }

    /** Cero si el monto no viene en la petición. */
    static Money copOrZero(String value) {
        return isBlank(value) ? Money.zero(COP) : cop(value);
    }

    /** {@code null} si el monto no viene en la petición. */
    static Money copOrNull(String value) {
        return isBlank(value) ? null : cop(value);
    }

    static String format(Money money) {
        return money == null ? null : money.amount().toPlainString();
    }

    /** Convierte un texto ya validado con el patrón de porcentajes. */
    static AnnualRate rate(String percent) {
        return AnnualRate.ofPercent(new BigDecimal(percent));
    }

    /** El porcentaje sin ceros de relleno: {@code "10.5"}. */
    static String format(AnnualRate rate) {
        return rate.percent().stripTrailingZeros().toPlainString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
