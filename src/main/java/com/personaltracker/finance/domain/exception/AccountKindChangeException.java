package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Se intentó cambiar el tipo de producto al editarlo (por ejemplo, de efectivo a tarjeta). */
public class AccountKindChangeException extends DomainException {

    public AccountKindChangeException() {
        super("finance.account.kind_change", "El tipo de producto no se puede cambiar.");
    }
}
