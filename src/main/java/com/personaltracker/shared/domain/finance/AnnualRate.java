package com.personaltracker.shared.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Tasa efectiva anual (EA) como fracción: {@code 0.245} es 24,5 % EA. Cabe en {@code NUMERIC(9,6)}:
 * hasta 6 decimales, es decir, porcentajes con hasta 4 decimales, entre 0 % y 1000 % (ADR-0018).
 */
public record AnnualRate(BigDecimal value) {

    public static final int SCALE = 6;
    public static final int PERCENT_SCALE = SCALE - 2;
    public static final BigDecimal MAX = BigDecimal.TEN;
    public static final AnnualRate ZERO = new AnnualRate(BigDecimal.ZERO);

    private static final int MONTHS_PER_YEAR = 12;

    public AnnualRate {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0 || value.compareTo(MAX) > 0 || value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidInterestRateException();
        }
        value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** Desde un porcentaje, por ejemplo {@code 24.5} para 24,5 % EA. */
    public static AnnualRate ofPercent(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        if (percent.stripTrailingZeros().scale() > PERCENT_SCALE) {
            throw new InvalidInterestRateException();
        }
        return new AnnualRate(percent.movePointLeft(2));
    }

    /** El porcentaje con 4 decimales, por ejemplo {@code 24.5000}. */
    public BigDecimal percent() {
        return value.movePointRight(2).setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    /** Tasa mensual equivalente: {@code (1 + EA)^(1/12) − 1}, con la precisión de {@link FinancialMath#CONTEXT}. */
    public BigDecimal monthly() {
        if (isZero()) {
            return BigDecimal.ZERO;
        }
        return FinancialMath.root(BigDecimal.ONE.add(value), MONTHS_PER_YEAR)
                .subtract(BigDecimal.ONE, FinancialMath.CONTEXT);
    }
}
