package com.personaltracker.finance.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Periodo de reporte (ADR-0013): un día, una semana de lunes a domingo o un mes calendario. Las fechas
 * son de negocio; "hoy" se calcula fuera del dominio con la zona horaria del usuario.
 */
public record Period(PeriodKind kind, DateRange range) {

    public Period {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
        if (!range.equals(rangeOf(kind, range.start()))) {
            throw new IllegalArgumentException("El rango " + range + " no es un periodo " + kind);
        }
    }

    public static Period containing(PeriodKind kind, LocalDate date) {
        return new Period(kind, rangeOf(kind, date));
    }

    public Period previous() {
        return containing(kind, range.start().minusDays(1));
    }

    /**
     * Rangos que se comparan en la fecha {@code today}. Un periodo en curso se compara hasta hoy con
     * los mismos días del periodo anterior, que se corta en su último día si es más corto (del 1 al 30
     * de marzo contra todo febrero). Un periodo cerrado, o que aún no empieza, se compara completo con
     * el anterior completo.
     */
    public PeriodComparison comparedAt(LocalDate today) {
        DateRange previousRange = previous().range();
        if (!range.contains(today) || today.equals(range.end())) {
            return new PeriodComparison(range, previousRange);
        }
        long elapsedDays = ChronoUnit.DAYS.between(range.start(), today);
        LocalDate sameDayBefore = previousRange.start().plusDays(elapsedDays);
        LocalDate previousEnd = sameDayBefore.isAfter(previousRange.end()) ? previousRange.end() : sameDayBefore;
        return new PeriodComparison(
                new DateRange(range.start(), today), new DateRange(previousRange.start(), previousEnd));
    }

    private static DateRange rangeOf(PeriodKind kind, LocalDate date) {
        return switch (kind) {
            case DAY -> new DateRange(date, date);
            case WEEK -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(monday, monday.plusDays(6));
            }
            case MONTH -> new DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }
}
