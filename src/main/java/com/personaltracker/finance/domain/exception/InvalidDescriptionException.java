package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La descripción del movimiento es demasiado larga. */
public class InvalidDescriptionException extends DomainException {

    public InvalidDescriptionException() {
        super("finance.transaction.description_invalid", "La descripción puede tener como máximo 200 caracteres.");
    }
}
