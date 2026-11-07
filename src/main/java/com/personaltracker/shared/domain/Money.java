package com.personaltracker.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Monto en una moneda (ADR-0006). Siempre tiene escala 2, igual que NUMERIC(19,2): como máximo 17
 * dígitos enteros y 2 decimales. Un monto con más decimales se rechaza en lugar de redondearse en
 * silencio; los cálculos que necesiten redondeo lo aplican de forma explícita.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;
    public static final int MAX_INTEGER_DIGITS = 17;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidMoneyAmountException();
        }
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidMoneyAmountException();
        }
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** Sumar monedas distintas es un error de programación: el dominio valida la moneda antes de operar. */
    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "No se pueden operar montos en " + currency + " y " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
