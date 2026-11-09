package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La llave está vacía o es demasiado larga. */
public class InvalidOwnKeyException extends DomainException {

    public InvalidOwnKeyException() {
        super("capture.own_key.invalid", "La llave debe tener entre 1 y 100 caracteres.");
    }
}
