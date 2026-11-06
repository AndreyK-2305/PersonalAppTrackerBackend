package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La cuenta está bloqueada temporalmente por intentos fallidos. La API lo expone como 429. */
public class AccountLockedException extends DomainException {

    public AccountLockedException() {
        super("identity.too_many_attempts", "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
    }
}
