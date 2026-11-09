package com.personaltracker.finance.api;

import com.personaltracker.shared.domain.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * El usuario borró un movimiento captado de una notificación (ADR-0015). Se publica dentro de la
 * transacción del borrado; {@code capture} marca la notificación como descartada. No se publica cuando
 * {@code capture} reemplaza un movimiento desde la revisión.
 */
public record TransactionDeleted(UserId owner, UUID transactionId) {

    public TransactionDeleted {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(transactionId, "transactionId");
    }
}
