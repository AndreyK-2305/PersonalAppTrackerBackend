package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El nombre del token está vacío o es demasiado largo, o no tiene permisos. */
public class InvalidDeviceTokenException extends DomainException {

    public InvalidDeviceTokenException() {
        super("identity.device_token_invalid",
                "El dispositivo necesita un nombre de hasta 60 caracteres y al menos un permiso.");
    }
}
