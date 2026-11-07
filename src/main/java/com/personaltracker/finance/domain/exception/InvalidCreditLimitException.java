package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El cupo de la tarjeta de crédito no es positivo. */
public class InvalidCreditLimitException extends DomainException {

    public InvalidCreditLimitException() {
        super("finance.account.credit_limit_invalid", "El cupo de la tarjeta debe ser mayor que cero.");
    }
}
