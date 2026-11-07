package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Se asignó una categoría de ingreso a un gasto, o al revés. */
public class CategoryKindMismatchException extends DomainException {

    public CategoryKindMismatchException() {
        super("finance.category.kind_mismatch", "La categoría no corresponde al tipo de movimiento.");
    }
}
