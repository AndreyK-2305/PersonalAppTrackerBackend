package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La llave no existe o pertenece a otro usuario. */
public class OwnKeyNotFoundException extends NotFoundException {

    public OwnKeyNotFoundException() {
        super("capture.own_key.not_found", "La llave no existe.");
    }
}
