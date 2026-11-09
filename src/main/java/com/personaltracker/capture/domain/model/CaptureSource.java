package com.personaltracker.capture.domain.model;

/**
 * Fuente de una notificación: entidad y canal. Dos avisos de fuentes distintas con el mismo producto
 * y monto pueden ser la misma compra (ADR-0017); dos de la misma fuente son compras distintas.
 */
public enum CaptureSource {
    BANCOLOMBIA_SMS,
    RAPPI_PUSH,
    /** Wallet repite lo que ya avisa el banco, con menos datos: su aviso se procesa después. */
    APPLE_WALLET
}
