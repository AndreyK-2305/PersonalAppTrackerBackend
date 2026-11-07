package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La categoría no existe o pertenece a otro usuario. */
public class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException() {
        super("finance.category.not_found", "La categoría no existe.");
    }
}
