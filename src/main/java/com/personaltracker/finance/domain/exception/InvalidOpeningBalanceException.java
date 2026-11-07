package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El saldo inicial no es válido para el tipo de producto. */
public class InvalidOpeningBalanceException extends DomainException {

    private InvalidOpeningBalanceException(String message) {
        super("finance.account.opening_balance_invalid", message);
    }

    public static InvalidOpeningBalanceException negativeCash() {
        return new InvalidOpeningBalanceException("El saldo inicial del efectivo no puede ser negativo.");
    }

    public static InvalidOpeningBalanceException negativeDebt() {
        return new InvalidOpeningBalanceException("La deuda inicial de la tarjeta no puede ser negativa.");
    }
}
