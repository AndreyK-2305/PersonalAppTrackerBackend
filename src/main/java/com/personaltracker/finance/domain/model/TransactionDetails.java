package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.InvalidDescriptionException;
import com.personaltracker.finance.domain.exception.InvalidMerchantException;
import com.personaltracker.shared.domain.DomainException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Datos descriptivos comunes a todos los movimientos. La descripción, el comercio y el medio de pago
 * son opcionales: un texto en blanco se guarda como nulo.
 */
public record TransactionDetails(LocalDate date, String description, String merchant, PaymentChannel channel) {

    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_MERCHANT_LENGTH = 100;

    public TransactionDetails {
        Objects.requireNonNull(date, "date");
        description = optionalText(description, MAX_DESCRIPTION_LENGTH, InvalidDescriptionException::new);
        merchant = optionalText(merchant, MAX_MERCHANT_LENGTH, InvalidMerchantException::new);
    }

    private static String optionalText(String value, int maxLength, Supplier<DomainException> invalid) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid.get();
        }
        return trimmed;
    }
}
