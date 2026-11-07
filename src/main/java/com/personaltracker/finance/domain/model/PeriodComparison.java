package com.personaltracker.finance.domain.model;

import java.util.Objects;

/** Rango actual y rango anterior equivalente con el que se compara. */
public record PeriodComparison(DateRange current, DateRange previous) {

    public PeriodComparison {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(previous, "previous");
    }
}
