package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La categoría está archivada y no se puede asignar ni recibir subcategorías. */
public class CategoryArchivedException extends DomainException {

    public CategoryArchivedException() {
        super("finance.category.archived", "La categoría está archivada. Reactívala para usarla.");
    }
}
