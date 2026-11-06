package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El nombre visible está vacío o es demasiado largo. */
public class InvalidDisplayNameException extends DomainException {

    public InvalidDisplayNameException() {
        super("identity.display_name_invalid", "El nombre debe tener entre 1 y 100 caracteres.");
    }
}
