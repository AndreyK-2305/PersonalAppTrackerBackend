package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** El usuario ya registró esa llave. */
public class OwnKeyTakenException extends ConflictException {

    public OwnKeyTakenException() {
        super("capture.own_key.taken", "Ya registraste esa llave.");
    }
}
