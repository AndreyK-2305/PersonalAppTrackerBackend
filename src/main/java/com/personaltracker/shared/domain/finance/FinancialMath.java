package com.personaltracker.shared.domain.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Operaciones financieras sobre {@link BigDecimal}, sin {@code double} (ADR-0006 y ADR-0018). Los
 * resultados intermedios usan {@link #CONTEXT}: 34 dígitos significativos.
 */
public final class FinancialMath {

    public static final MathContext CONTEXT = MathContext.DECIMAL128;

    /** Tope defensivo: partiendo por encima de la raíz, Newton converge mucho antes. */
    private static final int MAX_ITERATIONS = 10_000;

    private FinancialMath() {
    }

    /**
     * Raíz n-ésima de un número positivo por el método de Newton. Se parte de un valor que nunca queda
     * por debajo de la raíz, así que la sucesión baja de forma monótona y termina cuando deja de bajar.
     * Para x &gt; 1 ese valor es el menor entre {@code 1 + (x − 1)/n} (ajustado cuando x está cerca de 1)
     * y la potencia de 10 con los dígitos de la raíz (ajustada cuando x es grande).
     *
     * @throws ArithmeticException si no converge; nunca devuelve una aproximación a medias
     */
    public static BigDecimal root(BigDecimal x, int n) {
        Objects.requireNonNull(x, "x");
        if (n < 1) {
            throw new IllegalArgumentException("El índice de la raíz debe ser positivo: " + n);
        }
        if (x.signum() <= 0) {
            throw new IllegalArgumentException("Solo se calculan raíces de números positivos: " + x);
        }
        if (n == 1 || x.compareTo(BigDecimal.ONE) == 0) {
            return x;
        }
        BigDecimal degree = BigDecimal.valueOf(n);
        BigDecimal guess = startAboveTheRoot(x, n, degree);
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            BigDecimal powered = guess.pow(n - 1, CONTEXT);
            BigDecimal next = guess.multiply(degree.subtract(BigDecimal.ONE), CONTEXT)
                    .add(x.divide(powered, CONTEXT), CONTEXT)
                    .divide(degree, CONTEXT);
            if (next.compareTo(guess) >= 0) {
                return guess;
            }
            guess = next;
        }
        throw new ArithmeticException("La raíz " + n + " de " + x + " no convergió");
    }

    private static BigDecimal startAboveTheRoot(BigDecimal x, int n, BigDecimal degree) {
        if (x.compareTo(BigDecimal.ONE) < 0) {
            return BigDecimal.ONE;
        }
        // (1 + y)^n ≥ 1 + n·y, así que la raíz no supera 1 + (x − 1)/n.
        BigDecimal nearOne = BigDecimal.ONE.add(x.subtract(BigDecimal.ONE).divide(degree, CONTEXT), CONTEXT);
        // x < 10^d (d dígitos enteros), así que la raíz no supera 10^⌈d/n⌉.
        int integerDigits = x.precision() - x.scale();
        BigDecimal byMagnitude = BigDecimal.TEN.pow((integerDigits + n - 1) / n);
        return nearOne.min(byMagnitude);
    }

    /** Factor de crecimiento compuesto {@code (1 + rate)^periods}. */
    public static BigDecimal growth(BigDecimal rate, int periods) {
        if (periods < 0) {
            throw new IllegalArgumentException("El número de periodos no puede ser negativo: " + periods);
        }
        return BigDecimal.ONE.add(rate, CONTEXT).pow(periods, CONTEXT);
    }

    /** Redondea hacia arriba al peso, por ejemplo un valor recomendado que no debe quedarse corto. */
    public static BigDecimal ceilToUnit(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING);
    }
}
