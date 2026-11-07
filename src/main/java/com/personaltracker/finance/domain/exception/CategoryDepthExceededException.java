package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Las categorías tienen como máximo dos niveles (ADR-0014). */
public class CategoryDepthExceededException extends DomainException {

    public CategoryDepthExceededException() {
        super("finance.category.depth_exceeded", "Una subcategoría no puede tener subcategorías.");
    }
}
