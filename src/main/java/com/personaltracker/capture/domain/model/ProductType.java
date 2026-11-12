package com.personaltracker.capture.domain.model;

/** Tipo de producto que menciona la notificación. */
public enum ProductType {
    CREDIT_CARD,
    DEPOSIT,
    /**
     * Una tarjeta del banco sin saber si es de crédito o débito: Apple Wallet no lo dice. Coincide
     * con las tarjetas de crédito y las cuentas de depósito de esa entidad.
     */
    PAYMENT_CARD
}
