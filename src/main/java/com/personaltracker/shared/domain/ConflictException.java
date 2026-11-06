package com.personaltracker.shared.domain;

/**
 * La operación choca con el estado actual de los datos (por ejemplo, un valor que debe ser único y
 * ya existe). La API la expone como 409.
 */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
