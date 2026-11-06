package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El correo electrónico no tiene un formato válido. */
public class InvalidEmailException extends DomainException {

    public InvalidEmailException() {
        super("identity.email_invalid", "El correo electrónico no es válido.");
    }
}
