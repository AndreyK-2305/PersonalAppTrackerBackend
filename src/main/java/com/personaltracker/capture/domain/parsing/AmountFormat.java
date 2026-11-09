package com.personaltracker.capture.domain.parsing;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formato de monto que declara cada plantilla. No se adivina: con decimales obligatorios de 2
 * dígitos y grupos de miles de 3, un texto solo encaja en un formato. Por ejemplo, {@code 100.00}
 * es cien en {@link #ENGLISH} y no es válido en {@link #SPANISH}.
 */
public enum AmountFormat {

    /** Punto de miles y coma decimal opcional: {@code 25.005,00}, {@code 13.300}. */
    SPANISH(Pattern.compile("^[0-9]{1,3}(?:[.][0-9]{3})*(?:,[0-9]{2})?$"), ".", ","),

    /** Coma de miles y punto decimal opcional: {@code 6,500.00}, {@code 480,000}. */
    ENGLISH(Pattern.compile("^[0-9]{1,3}(?:,[0-9]{3})*(?:[.][0-9]{2})?$"), ",", ".");

    private final Pattern pattern;
    private final String groupSeparator;
    private final String decimalSeparator;

    AmountFormat(Pattern pattern, String groupSeparator, String decimalSeparator) {
        this.pattern = pattern;
        this.groupSeparator = groupSeparator;
        this.decimalSeparator = decimalSeparator;
    }

    /** El monto, o vacío si el texto no tiene exactamente este formato. */
    public Optional<BigDecimal> parse(String text) {
        if (text == null || !pattern.matcher(text).matches()) {
            return Optional.empty();
        }
        String plain = text.replace(groupSeparator, "").replace(decimalSeparator, ".");
        return Optional.of(new BigDecimal(plain));
    }
}
