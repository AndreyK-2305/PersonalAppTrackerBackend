package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El nombre de la categoría está vacío o es demasiado largo. */
public class InvalidCategoryNameException extends DomainException {

    public InvalidCategoryNameException() {
        super("finance.category.name_invalid", "El nombre de la categoría debe tener entre 1 y 50 caracteres.");
    }
}
