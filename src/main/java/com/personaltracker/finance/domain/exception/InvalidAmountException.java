package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El monto de un movimiento no es positivo. */
public class InvalidAmountException extends DomainException {

    public InvalidAmountException() {
        super("finance.transaction.amount_invalid", "El monto debe ser mayor que cero.");
    }
}
