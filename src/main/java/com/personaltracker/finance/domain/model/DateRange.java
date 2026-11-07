package com.personaltracker.finance.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Rango de fechas de negocio, con ambos extremos incluidos. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("El rango termina antes de empezar: " + start + " a " + end);
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
