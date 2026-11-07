package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** El movimiento no existe o pertenece a otro usuario. */
public class TransactionNotFoundException extends NotFoundException {

    public TransactionNotFoundException() {
        super("finance.transaction.not_found", "El movimiento no existe.");
    }
}
