package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.UnsupportedCurrencyException;
import java.util.Currency;

/** Monedas admitidas. El MVP solo admite pesos colombianos y no hace conversiones (ADR-0013). */
public final class SupportedCurrencies {

    public static final Currency COP = Currency.getInstance("COP");

    private SupportedCurrencies() {
    }

    public static Currency require(Currency currency) {
        if (!COP.equals(currency)) {
            throw new UnsupportedCurrencyException();
        }
        return currency;
    }
}
