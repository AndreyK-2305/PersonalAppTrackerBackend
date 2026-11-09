package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Los nombres o los apellidos son demasiado largos. */
public class InvalidPersonNameException extends DomainException {

    public InvalidPersonNameException() {
        super("identity.person_name_invalid", "Los nombres y los apellidos pueden tener hasta 100 caracteres.");
    }
}
