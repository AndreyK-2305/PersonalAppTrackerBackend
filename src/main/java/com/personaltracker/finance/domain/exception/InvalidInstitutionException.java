package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La entidad del producto está vacía o es demasiado larga. */
public class InvalidInstitutionException extends DomainException {

    public InvalidInstitutionException() {
        super("finance.account.institution_invalid", "La entidad debe tener entre 1 y 60 caracteres.");
    }
}
