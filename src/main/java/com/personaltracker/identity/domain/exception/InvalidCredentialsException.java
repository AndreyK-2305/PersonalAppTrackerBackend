package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Credenciales inválidas. El mismo error para cuenta inexistente o contraseña incorrecta evita revelar qué cuentas existen. La API lo expone como 401. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("identity.invalid_credentials", "El correo o la contraseña no son correctos.");
    }
}
