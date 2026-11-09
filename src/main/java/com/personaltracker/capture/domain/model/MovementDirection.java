package com.personaltracker.capture.domain.model;

/** Sentido del dinero respecto del producto propio. */
public enum MovementDirection {
    /** Sale del producto: compra, pago o transferencia enviada. */
    OUTFLOW,
    /** Entra al producto: transferencia recibida, pago recibido o consignación. */
    INFLOW
}
