package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El registro está cerrado (REGISTRATION_ENABLED=false). La API lo expone como 403. */
public class RegistrationClosedException extends DomainException {

    public RegistrationClosedException() {
        super("identity.registration_closed", "El registro de nuevas cuentas no está habilitado.");
    }
}
