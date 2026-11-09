package com.personaltracker.capture.domain.parsing;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fechas y horas de los textos bancarios: {@code dd/MM/yy} o {@code dd/MM/yyyy}, y {@code HH:mm}. */
final class BankDateTime {

    static final String DATE = "[0-9]{2}/[0-9]{2}/(?:[0-9]{2}|[0-9]{4})";
    static final String TIME = "[0-9]{2}:[0-9]{2}";

    private static final Pattern DATE_PARTS = Pattern.compile("^([0-9]{2})/([0-9]{2})/([0-9]{2}|[0-9]{4})$");
    private static final Pattern TIME_PARTS = Pattern.compile("^([0-9]{2}):([0-9]{2})$");

    private BankDateTime() {}

    /** La fecha y hora local, o vacío si no es una fecha real (por ejemplo, 31/02). */
    static Optional<LocalDateTime> parse(String date, String time) {
        Matcher d = DATE_PARTS.matcher(date);
        Matcher t = TIME_PARTS.matcher(time);
        if (!d.matches() || !t.matches()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(d.group(3));
        if (d.group(3).length() == 2) {
            year += 2000;
        }
        try {
            return Optional.of(LocalDateTime.of(
                    year,
                    Integer.parseInt(d.group(2)),
                    Integer.parseInt(d.group(1)),
                    Integer.parseInt(t.group(1)),
                    Integer.parseInt(t.group(2))));
        } catch (DateTimeException invalid) {
            return Optional.empty();
        }
    }
}
