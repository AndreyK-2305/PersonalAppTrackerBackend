package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La cuenta no existe (por ejemplo, una sesión de una cuenta ya eliminada). */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("identity.user_not_found", "La cuenta no existe.");
    }
}
