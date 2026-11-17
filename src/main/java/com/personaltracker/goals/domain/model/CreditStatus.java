package com.personaltracker.goals.domain.model;

/** Estado de un crédito en un día dado. Es informativo: el simulador no cobra mora (ADR-0018). */
public enum CreditStatus {
    /** La próxima cuota todavía no vence. */
    ON_TRACK,
    /** La próxima cuota ya venció y no está marcada como pagada. */
    OVERDUE,
    /** No queda saldo. */
    PAID_OFF
}
