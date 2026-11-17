package com.personaltracker.goals.domain.model;

/** Qué hace un abono extraordinario a capital con el resto del plan (ADR-0018). */
public enum PrepaymentMode {
    /** Conserva la cuota y termina antes. Es el modo por defecto. */
    REDUCE_TERM,
    /** Conserva las cuotas que faltan y baja el valor de cada una. */
    REDUCE_INSTALLMENT
}
