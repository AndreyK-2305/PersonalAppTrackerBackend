package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El nombre del producto está vacío o es demasiado largo. */
public class InvalidAccountNameException extends DomainException {

    public InvalidAccountNameException() {
        super("finance.account.name_invalid", "El nombre del producto debe tener entre 1 y 60 caracteres.");
    }
}
