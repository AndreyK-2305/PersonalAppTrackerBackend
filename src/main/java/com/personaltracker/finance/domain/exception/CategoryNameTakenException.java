package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** El nombre de la categoría ya está en uso entre sus hermanas del mismo tipo. */
public class CategoryNameTakenException extends ConflictException {

    public CategoryNameTakenException() {
        super("finance.category.name_taken", "Ya existe una categoría con ese nombre en el mismo nivel.");
    }
}
