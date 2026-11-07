package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.ConflictException;

/** Una categoría con movimientos o subcategorías no se borra: se archiva (ADR-0014). */
public class CategoryInUseException extends ConflictException {

    public CategoryInUseException() {
        super("finance.category.in_use", "La categoría tiene movimientos o subcategorías. Archívala en lugar de borrarla.");
    }
}
