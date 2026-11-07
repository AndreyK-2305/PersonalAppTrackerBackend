package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.InvalidLastFourDigitsException;
import java.util.regex.Pattern;

/**
 * Últimos 4 dígitos de una tarjeta o cuenta: identifican el producto sin guardar el número completo,
 * que nunca se almacena.
 */
public record LastFourDigits(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{4}$");

    public LastFourDigits {
        String trimmed = value == null ? "" : value.trim();
        if (!FORMAT.matcher(trimmed).matches()) {
            throw new InvalidLastFourDigitsException();
        }
        value = trimmed;
    }
}
