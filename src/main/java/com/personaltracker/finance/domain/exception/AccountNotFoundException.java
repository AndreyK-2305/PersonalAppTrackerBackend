package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** El producto no existe o pertenece a otro usuario. */
public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException() {
        super("finance.account.not_found", "El producto no existe.");
    }
}
