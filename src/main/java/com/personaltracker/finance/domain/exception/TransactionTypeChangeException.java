package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Se intentó cambiar el tipo de un movimiento al editarlo (ADR-0015). */
public class TransactionTypeChangeException extends DomainException {

    public TransactionTypeChangeException() {
        super("finance.transaction.type_change", "El tipo de movimiento no se puede cambiar. Bórralo y regístralo de nuevo.");
    }
}
