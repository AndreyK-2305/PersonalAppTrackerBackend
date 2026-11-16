package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException.Rule;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.Amortization;
import com.personaltracker.shared.domain.finance.AnnualRate;
import com.personaltracker.shared.domain.finance.FinancialMath;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Condiciones de una financiación (ADR-0018): valor del bien, cuota inicial, tasa EA, plazo, fecha de
 * la primera cuota y, si se conoce, la cuota real que cobra la entidad. De aquí sale todo el plan de
 * pagos ({@link PaymentSchedule}), que no se guarda.
 *
 * @param downPayment cuota inicial; cero si no hay
 * @param actualInstallment cuota real por periodo, o {@code null} si se usa la recomendada
 */
public record FinancedTerms(
        Money assetValue,
        Money downPayment,
        AnnualRate rate,
        int installments,
        LocalDate firstDueDate,
        Money actualInstallment) {

    /** 50 años de cuotas mensuales. */
    public static final int MAX_INSTALLMENTS = 600;

    /** Distancia máxima, hacia atrás o hacia adelante, entre hoy y la primera cuota. */
    public static final int MAX_YEARS_FROM_TODAY = 100;

    public FinancedTerms {
        Objects.requireNonNull(assetValue, "assetValue");
        Objects.requireNonNull(downPayment, "downPayment");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(firstDueDate, "firstDueDate");
        if (!assetValue.isPositive()) {
            throw new InvalidFinancedGoalException(Rule.ASSET_VALUE, "El valor del bien debe ser mayor que cero.");
        }
        if (!downPayment.currency().equals(assetValue.currency()) || downPayment.isNegative()
                || downPayment.amount().compareTo(assetValue.amount()) >= 0) {
            throw new InvalidFinancedGoalException(Rule.DOWN_PAYMENT,
                    "La cuota inicial debe ser menor que el valor del bien.");
        }
        if (installments < 1 || installments > MAX_INSTALLMENTS) {
            throw new InvalidFinancedGoalException(Rule.INSTALLMENTS,
                    "El número de cuotas debe estar entre 1 y " + MAX_INSTALLMENTS + ".");
        }
        if (actualInstallment != null) {
            Money recommended = recommendedInstallment(assetValue.minus(downPayment), rate, installments);
            if (!actualInstallment.currency().equals(assetValue.currency())
                    || actualInstallment.amount().compareTo(recommended.amount()) < 0) {
                throw new InvalidFinancedGoalException(Rule.ACTUAL_INSTALLMENT,
                        "La cuota real no puede ser menor que la cuota calculada con esa tasa y ese plazo.");
            }
        }
    }

    /** Lo que se financia: el valor del bien menos la cuota inicial. */
    public Money financedAmount() {
        return assetValue.minus(downPayment);
    }

    /** Cuota fija calculada con la tasa y el plazo, redondeada hacia arriba al peso. */
    public Money recommendedInstallment() {
        return recommendedInstallment(financedAmount(), rate, installments);
    }

    public PaymentSchedule schedule() {
        return PaymentSchedule.of(this);
    }

    /** La primera cuota no puede estar a más de {@link #MAX_YEARS_FROM_TODAY} años de hoy. */
    public void requireFirstDueDateNear(LocalDate today) {
        if (firstDueDate.isBefore(today.minusYears(MAX_YEARS_FROM_TODAY))
                || firstDueDate.isAfter(today.plusYears(MAX_YEARS_FROM_TODAY))) {
            throw new InvalidFinancedGoalException(Rule.FIRST_DUE_DATE, "La fecha de la primera cuota debe estar a"
                    + " menos de " + MAX_YEARS_FROM_TODAY + " años de hoy.");
        }
    }

    private static Money recommendedInstallment(Money financed, AnnualRate rate, int installments) {
        return new Money(
                FinancialMath.ceilToUnit(Amortization.fixedInstallment(financed.amount(), rate.monthly(), installments)),
                financed.currency());
    }
}
