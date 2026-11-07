package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La moneda no está entre las admitidas (ADR-0013). */
public class UnsupportedCurrencyException extends DomainException {

    public UnsupportedCurrencyException() {
        super("finance.currency.unsupported", "Por ahora solo se admiten pesos colombianos (COP).");
    }
}
