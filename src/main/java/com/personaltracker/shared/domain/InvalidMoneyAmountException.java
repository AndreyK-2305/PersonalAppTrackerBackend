package com.personaltracker.shared.domain;

/** El monto no cabe en NUMERIC(19,2): tiene más de 2 decimales o más de 17 dígitos enteros. */
public class InvalidMoneyAmountException extends DomainException {

    public InvalidMoneyAmountException() {
        super("money.amount_invalid", "El monto debe tener como máximo 17 dígitos enteros y 2 decimales.");
    }
}
