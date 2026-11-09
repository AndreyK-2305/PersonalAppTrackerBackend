package com.personaltracker.finance.domain.model;

/** Cómo llegó el movimiento: registrado a mano o captado de una notificación bancaria (ADR-0017). */
public enum TransactionOrigin {
    MANUAL,
    CAPTURE
}
