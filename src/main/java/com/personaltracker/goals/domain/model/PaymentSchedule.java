package com.personaltracker.goals.domain.model;

import com.personaltracker.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * Plan de pagos de una financiación (ADR-0018): la tabla de amortización con sus fechas y los
 * totales. Incluye las cuotas ya pagadas y las pendientes; sin registros, es el plan original. Lo
 * calcula {@link CreditPlan} y no se guarda.
 *
 * @param recommendedInstallment cuota fija calculada que rige hoy, redondeada hacia arriba al peso
 * @param installmentAmount lo que se paga cada mes: la cuota calculada más los seguros y otros
 * @param extrasPerInstallment "seguros y otros": la parte de la cuota que no amortiza
 * @param totalPaid todo lo que se paga en el plan: cuotas, con sus seguros y otros, y abonos
 * @param totalCost cuota inicial más {@code totalPaid}
 */
public record PaymentSchedule(
        Money financedAmount,
        Money recommendedInstallment,
        Money installmentAmount,
        Money extrasPerInstallment,
        List<Installment> installments,
        Money totalInterest,
        Money totalExtras,
        Money totalPaid,
        Money totalCost,
        LocalDate lastDueDate) {

    public PaymentSchedule {
        installments = List.copyOf(installments);
    }

    /**
     * Cuota de la tabla. {@code payment} es todo lo que se paga ese mes: interés, capital, seguros y
     * otros y, si se pagó de más, el abono extraordinario ({@code extraPrincipal}). {@code balance} es
     * el saldo después de pagarla.
     *
     * @param paidOn fecha en que se pagó, o {@code null} si está pendiente
     */
    public record Installment(
            int number,
            LocalDate dueDate,
            Money payment,
            Money interest,
            Money principal,
            Money extras,
            Money extraPrincipal,
            Money balance,
            LocalDate paidOn) {

        public boolean paid() {
            return paidOn != null;
        }
    }

    /** El plan original de unas condiciones, sin pagos ni abonos. */
    static PaymentSchedule of(FinancedTerms terms) {
        return CreditPlan.of(terms, List.of()).schedule();
    }
}
