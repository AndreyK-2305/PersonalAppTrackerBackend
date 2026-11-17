package com.personaltracker.shared.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Amortización con cuota fija (sistema francés), en {@link BigDecimal} (ADR-0018). El interés de cada
 * periodo se calcula sobre el saldo al inicio del periodo y se redondea al centavo.
 */
public final class Amortization {

    /** Decimales de los valores de la tabla: centavos, como {@code NUMERIC(19,2)}. */
    public static final int SCALE = 2;

    private Amortization() {
    }

    /**
     * Cuota fija exacta, sin redondear: {@code P · i ÷ (1 − (1+i)^−n)}, o {@code P ÷ n} si la tasa es 0.
     * Quien la usa decide el redondeo.
     */
    public static BigDecimal fixedInstallment(BigDecimal principal, BigDecimal periodRate, int periods) {
        requireTerms(principal, periodRate, periods);
        if (periodRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(periods), FinancialMath.CONTEXT);
        }
        BigDecimal growth = FinancialMath.growth(periodRate, periods);
        return principal.multiply(periodRate, FinancialMath.CONTEXT)
                .multiply(growth, FinancialMath.CONTEXT)
                .divide(growth.subtract(BigDecimal.ONE), FinancialMath.CONTEXT);
    }

    /**
     * Tabla de amortización con la cuota indicada. En cada periodo el interés sale del saldo y el resto
     * de la cuota abona capital; el último periodo paga el saldo que quede, así que termina en 0.
     *
     * <ul>
     *   <li>Si la cuota no alcanza a cubrir el interés de un periodo, ese periodo no abona capital.</li>
     *   <li>Si la cuota salda la deuda antes del último periodo, la tabla termina ahí y tiene menos
     *       filas que {@code periods}.</li>
     * </ul>
     *
     * @param principal monto financiado, positivo y con hasta 2 decimales
     * @param installment cuota por periodo, positiva y con hasta 2 decimales
     */
    public static List<Row> schedule(BigDecimal principal, BigDecimal periodRate, int periods, BigDecimal installment) {
        requireTerms(principal, periodRate, periods);
        Objects.requireNonNull(installment, "installment");
        if (installment.signum() <= 0) {
            throw new IllegalArgumentException("La cuota debe ser positiva: " + installment);
        }
        BigDecimal payment = cents(installment, "La cuota");
        BigDecimal balance = cents(principal, "El monto financiado");
        List<Row> rows = new ArrayList<>(periods);
        for (int number = 1; number <= periods && balance.signum() > 0; number++) {
            Row row = row(number, balance, periodRate, number == periods, payment);
            balance = row.balance();
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    /**
     * La siguiente cuota de un crédito en curso: la primera fila de {@link #schedule} para ese saldo.
     * Sirve para registrar los pagos uno a uno sin recalcular toda la tabla.
     *
     * @param remainingPeriods cuotas que faltan, incluida esta; si es 1, la cuota paga todo el saldo
     */
    public static Row nextRow(BigDecimal balance, BigDecimal periodRate, int remainingPeriods, BigDecimal installment) {
        requireTerms(balance, periodRate, remainingPeriods);
        Objects.requireNonNull(installment, "installment");
        if (installment.signum() <= 0) {
            throw new IllegalArgumentException("La cuota debe ser positiva: " + installment);
        }
        return row(1, cents(balance, "El saldo"), periodRate, remainingPeriods == 1, cents(installment, "La cuota"));
    }

    private static Row row(int number, BigDecimal balance, BigDecimal periodRate, boolean last, BigDecimal payment) {
        BigDecimal interest = balance.multiply(periodRate, FinancialMath.CONTEXT)
                .setScale(SCALE, RoundingMode.HALF_EVEN);
        BigDecimal capital = payment.subtract(interest).max(BigDecimal.ZERO.setScale(SCALE));
        if (last || capital.compareTo(balance) > 0) {
            capital = balance;
        }
        return new Row(number, interest.add(capital), interest, capital, balance.subtract(capital));
    }

    private static void requireTerms(BigDecimal principal, BigDecimal periodRate, int periods) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(periodRate, "periodRate");
        if (principal.signum() <= 0) {
            throw new IllegalArgumentException("El monto financiado debe ser positivo: " + principal);
        }
        if (periodRate.signum() < 0) {
            throw new IllegalArgumentException("La tasa no puede ser negativa: " + periodRate);
        }
        if (periods < 1) {
            throw new IllegalArgumentException("Debe haber al menos un periodo: " + periods);
        }
    }

    private static BigDecimal cents(BigDecimal value, String label) {
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException(label + " admite como máximo " + SCALE + " decimales: " + value);
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * Fila de la tabla: lo que se paga en el periodo, cuánto es interés y cuánto capital, y el saldo
     * que queda después.
     */
    public record Row(int number, BigDecimal payment, BigDecimal interest, BigDecimal principal, BigDecimal balance) {}
}
