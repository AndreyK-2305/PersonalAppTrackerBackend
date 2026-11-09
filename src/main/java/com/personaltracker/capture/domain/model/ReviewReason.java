package com.personaltracker.capture.domain.model;

/** Por qué una notificación quedó "Por revisar" (ADR-0017). */
public enum ReviewReason {
    /** Ninguna plantilla coincidió exactamente: no hay movimiento, solo el texto. */
    UNRECOGNIZED_TEXT,
    /** Varios productos coinciden: el movimiento se registró en "Otro". */
    AMBIGUOUS_PRODUCT,
    /** El destinatario tiene el nombre del usuario: se registró como gasto y se propone "Es mía". */
    POSSIBLE_OWN_TRANSFER,
    /** Compra en dólares sin TRM disponible: no hay movimiento hasta que el usuario escriba el valor. */
    MISSING_EXCHANGE_RATE;

    /** Si el motivo deja un movimiento registrado mientras se revisa. */
    public boolean hasMovement() {
        return this != UNRECOGNIZED_TEXT && this != MISSING_EXCHANGE_RATE;
    }
}
