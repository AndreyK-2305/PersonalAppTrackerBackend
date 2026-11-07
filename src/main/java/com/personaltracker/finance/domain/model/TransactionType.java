package com.personaltracker.finance.domain.model;

/** Tipo de movimiento. Cada tipo corresponde a una subclase de {@link Transaction}. */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    ADJUSTMENT
}
