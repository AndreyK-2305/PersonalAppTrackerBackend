package com.personaltracker.finance.domain.model;

/** Tipo de producto. Cada tipo corresponde a una subclase de {@link Account}. */
public enum AccountKind {
    CASH,
    SAVINGS,
    CHECKING,
    DIGITAL_WALLET,
    CREDIT_CARD,
    OTHER
}
