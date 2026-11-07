package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Los últimos dígitos no son exactamente 4 números. */
public class InvalidLastFourDigitsException extends DomainException {

    public InvalidLastFourDigitsException() {
        super("finance.account.last_four_invalid", "Los últimos dígitos deben ser exactamente 4 números.");
    }
}
