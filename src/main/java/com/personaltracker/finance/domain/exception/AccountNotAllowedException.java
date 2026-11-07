package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Una transferencia o un ajuste usa un producto que no lleva saldo. */
public class AccountNotAllowedException extends DomainException {

    public AccountNotAllowedException() {
        super("finance.transaction.account_not_allowed", "El producto «Otro» solo admite ingresos y gastos.");
    }
}
