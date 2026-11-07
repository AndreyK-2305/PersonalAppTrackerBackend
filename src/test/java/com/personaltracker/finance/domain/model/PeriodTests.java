package com.personaltracker.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodTests {

    @Test
    void weeksRunFromMondayToSunday() {
        // 2026-11-06 es viernes.
        Period week = Period.containing(PeriodKind.WEEK, LocalDate.of(2026, 11, 6));

        assertThat(week.range()).isEqualTo(range("2026-11-02", "2026-11-08"));
        assertThat(Period.containing(PeriodKind.WEEK, LocalDate.of(2026, 11, 2))).isEqualTo(week);
        assertThat(Period.containing(PeriodKind.WEEK, LocalDate.of(2026, 11, 8))).isEqualTo(week);
        assertThat(week.previous().range()).isEqualTo(range("2026-10-26", "2026-11-01"));
    }

    @Test
    void monthsAreCalendarMonths() {
        Period february = Period.containing(PeriodKind.MONTH, LocalDate.of(2028, 2, 10));

        assertThat(february.range()).isEqualTo(range("2028-02-01", "2028-02-29"));
        assertThat(february.previous().range()).isEqualTo(range("2028-01-01", "2028-01-31"));
        assertThat(Period.containing(PeriodKind.MONTH, LocalDate.of(2026, 1, 15)).previous().range())
                .isEqualTo(range("2025-12-01", "2025-12-31"));
    }

    @Test
    void daysAreSingleDates() {
        Period day = Period.containing(PeriodKind.DAY, LocalDate.of(2026, 11, 1));

        assertThat(day.range()).isEqualTo(range("2026-11-01", "2026-11-01"));
        assertThat(day.previous().range()).isEqualTo(range("2026-10-31", "2026-10-31"));
    }

    @Test
    void monthInProgressIsComparedWithTheSameDaysOfThePreviousMonth() {
        Period november = Period.containing(PeriodKind.MONTH, LocalDate.of(2026, 11, 6));

        PeriodComparison comparison = november.comparedAt(LocalDate.of(2026, 11, 6));

        assertThat(comparison.current()).isEqualTo(range("2026-11-01", "2026-11-06"));
        assertThat(comparison.previous()).isEqualTo(range("2026-10-01", "2026-10-06"));
    }

    @Test
    void comparisonIsCutAtTheEndOfAShorterPreviousMonth() {
        Period march = Period.containing(PeriodKind.MONTH, LocalDate.of(2026, 3, 30));

        PeriodComparison comparison = march.comparedAt(LocalDate.of(2026, 3, 30));

        assertThat(comparison.current()).isEqualTo(range("2026-03-01", "2026-03-30"));
        assertThat(comparison.previous()).isEqualTo(range("2026-02-01", "2026-02-28"));
    }

    @Test
    void weekInProgressIsComparedWithTheSameWeekdaysOfThePreviousWeek() {
        Period week = Period.containing(PeriodKind.WEEK, LocalDate.of(2026, 11, 4));

        PeriodComparison comparison = week.comparedAt(LocalDate.of(2026, 11, 4));

        assertThat(comparison.current()).isEqualTo(range("2026-11-02", "2026-11-04"));
        assertThat(comparison.previous()).isEqualTo(range("2026-10-26", "2026-10-28"));
    }

    @Test
    void closedPeriodsAndTheirLastDayCompareComplete() {
        Period october = Period.containing(PeriodKind.MONTH, LocalDate.of(2026, 10, 1));

        assertThat(october.comparedAt(LocalDate.of(2026, 11, 6)))
                .isEqualTo(new PeriodComparison(range("2026-10-01", "2026-10-31"), range("2026-09-01", "2026-09-30")));
        assertThat(october.comparedAt(LocalDate.of(2026, 10, 31)).current()).isEqualTo(october.range());
    }

    @Test
    void todayIsComparedWithTheWholePreviousDay() {
        Period today = Period.containing(PeriodKind.DAY, LocalDate.of(2026, 11, 6));

        assertThat(today.comparedAt(LocalDate.of(2026, 11, 6)))
                .isEqualTo(new PeriodComparison(range("2026-11-06", "2026-11-06"), range("2026-11-05", "2026-11-05")));
    }

    @Test
    void rejectsRangesThatAreNotAPeriodOfItsKind() {
        assertThatThrownBy(() -> new Period(PeriodKind.WEEK, range("2026-11-03", "2026-11-09")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> range("2026-11-09", "2026-11-03")).isInstanceOf(IllegalArgumentException.class);
    }

    private static DateRange range(String start, String end) {
        return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
    }
}
