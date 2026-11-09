package com.personaltracker.capture.domain.model;

/** Estado de una notificación en la cola (ADR-0017). */
public enum CaptureStatus {
    /** Guardada por el endpoint; se analiza pasado {@link CapturedNotification#SETTLE_DELAY}. */
    RECEIVED,
    /** Generó un movimiento sin dudas. */
    RECORDED,
    /** Necesita que el usuario la revise; puede tener un movimiento registrado. */
    REVIEW,
    /** Repite una compra que otra fuente ya registró: no genera movimiento. */
    PAIRED,
    /** No es un movimiento, o el usuario borró el movimiento que generó. */
    DISCARDED
}
