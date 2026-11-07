package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Un filtro de fechas termina antes de empezar. */
public class InvalidDateRangeException extends DomainException {

    public InvalidDateRangeException() {
        super("finance.date_range.invalid", "La fecha inicial no puede ser posterior a la final.");
    }
}
