package com.personaltracker.capture.domain.parsing;

import java.text.Normalizer;
import java.util.Locale;

/** Normalización del texto antes de aplicar las plantillas. */
final class NotificationText {

    private NotificationText() {}

    /** Une saltos de línea y espacios (incluidos los de no separación) en un solo espacio. */
    static String collapseSpaces(String text) {
        return text.replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    /** Minúsculas y sin tildes, para comparar nombres de apps. */
    static String simplify(String text) {
        String decomposed = Normalizer.normalize(collapseSpaces(text), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
