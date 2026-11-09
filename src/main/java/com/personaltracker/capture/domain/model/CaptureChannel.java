package com.personaltracker.capture.domain.model;

/**
 * Medio de pago que se deduce de la plantilla. Se traduce al medio de pago de {@code finance} al
 * registrar el movimiento.
 */
public enum CaptureChannel {
    CARD,
    BANK_TRANSFER,
    DEPOSIT
}
