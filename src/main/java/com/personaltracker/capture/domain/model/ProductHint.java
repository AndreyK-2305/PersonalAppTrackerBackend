package com.personaltracker.capture.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lo que la notificación dice del producto propio: entidad, tipo y, si los trae, los últimos 4
 * dígitos. Con esto se busca el producto del usuario (ADR-0017).
 */
public record ProductHint(CaptureInstitution institution, ProductType type, Optional<String> lastFourDigits) {

    private static final Pattern LAST_FOUR = Pattern.compile("^[0-9]{4}$");

    public ProductHint {
        Objects.requireNonNull(institution, "institution");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lastFourDigits, "lastFourDigits");
        lastFourDigits.ifPresent(digits -> {
            if (!LAST_FOUR.matcher(digits).matches()) {
                throw new IllegalArgumentException("Se esperaban 4 dígitos");
            }
        });
    }

    public static ProductHint of(CaptureInstitution institution, ProductType type, String lastFourDigits) {
        return new ProductHint(institution, type, Optional.of(lastFourDigits));
    }

    public static ProductHint withoutDigits(CaptureInstitution institution, ProductType type) {
        return new ProductHint(institution, type, Optional.empty());
    }
}
