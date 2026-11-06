package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La zona horaria no es un identificador de región válido (por ejemplo, America/Bogota). */
public class InvalidTimeZoneException extends DomainException {

    public InvalidTimeZoneException() {
        super("identity.time_zone_invalid", "La zona horaria no es válida.");
    }
}
