package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** Ya existe una cuenta con el correo indicado. */
public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("identity.email_already_registered", "Ya existe una cuenta con este correo electrónico.");
    }
}
