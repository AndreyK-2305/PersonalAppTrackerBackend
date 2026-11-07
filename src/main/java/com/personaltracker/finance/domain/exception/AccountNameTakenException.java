package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** El nombre del producto ya está en uso por el mismo usuario. */
public class AccountNameTakenException extends ConflictException {

    public AccountNameTakenException() {
        super("finance.account.name_taken", "Ya tienes un producto con ese nombre.");
    }
}
