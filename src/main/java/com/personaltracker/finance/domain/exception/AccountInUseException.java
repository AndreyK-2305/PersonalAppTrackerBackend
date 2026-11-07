package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** Un producto con movimientos no se borra: se archiva (ADR-0014). */
public class AccountInUseException extends ConflictException {

    public AccountInUseException() {
        super("finance.account.has_transactions", "El producto tiene movimientos. Archívalo en lugar de borrarlo.");
    }
}
