package com.personaltracker.shared.domain;

/**
 * El recurso no existe o no pertenece al usuario autenticado. Ambos casos responden 404 para no
 * revelar la existencia de recursos de otros usuarios.
 */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}
