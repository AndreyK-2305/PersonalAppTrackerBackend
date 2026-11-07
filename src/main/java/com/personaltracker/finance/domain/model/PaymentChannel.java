package com.personaltracker.finance.domain.model;

/**
 * Medio por el que se movió el dinero. Solo describe el movimiento: no cambia saldos ni reportes,
 * por eso es un enum y no una jerarquía de clases (ADR-0013).
 */
public enum PaymentChannel {
    CARD,
    BANK_TRANSFER,
    PSE,
    CASH,
    DEPOSIT,
    WITHDRAWAL,
    DIRECT_DEBIT,
    OTHER
}
