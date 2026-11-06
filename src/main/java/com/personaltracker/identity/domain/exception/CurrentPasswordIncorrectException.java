package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La contraseña actual indicada al cambiarla no coincide. */
public class CurrentPasswordIncorrectException extends DomainException {

    public CurrentPasswordIncorrectException() {
        super("identity.current_password_incorrect", "La contraseña actual no es correcta.");
    }
}
