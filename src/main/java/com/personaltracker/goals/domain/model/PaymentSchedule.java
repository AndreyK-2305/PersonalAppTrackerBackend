package com.personaltracker.goals.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.Amortization;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Plan de pagos de una financiación (ADR-0018): la tabla de amortización con sus fechas y los
 * totales. Sale completo de {@link FinancedTerms} y no se guarda.
 *
 * @param recommendedInstallment cuota fija calculada, redondeada hacia arriba al peso
 * @param installmentAmount lo que se paga cada mes: la cuota real si se registró, o la recomendada
 * @param extrasPerInstallment "seguros y otros": la parte de la cuota real que no amortiza
 * @param totalPaid suma de todas las cuotas, con sus seguros y otros
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
     * Cuota de la tabla. {@code payment} es todo lo que se paga ese mes: interés, capital y seguros y
     * otros. {@code balance} es el saldo después de pagarla.
     */
    public record Installment(
            int number, LocalDate dueDate, Money payment, Money interest, Money principal, Money extras, Money balance) {}

    static PaymentSchedule of(FinancedTerms terms) {
        Currency currency = terms.assetValue().currency();
        Money financed = terms.financedAmount();
        Money recommended = terms.recommendedInstallment();
        Money installmentAmount = terms.actualInstallment() == null ? recommended : terms.actualInstallment();
        Money extras = installmentAmount.minus(recommended);

        List<Installment> rows = Amortization
                .schedule(financed.amount(), terms.rate().monthly(), terms.installments(), recommended.amount())
                .stream()
                .map(row -> new Installment(
                        row.number(),
                        terms.firstDueDate().plusMonths(row.number() - 1L),
                        new Money(row.payment(), currency).plus(extras),
                        new Money(row.interest(), currency),
                        new Money(row.principal(), currency),
                        extras,
                        new Money(row.balance(), currency)))
                .toList();

        Money zero = Money.zero(currency);
        Money totalInterest = rows.stream().map(Installment::interest).reduce(zero, Money::plus);
        Money totalExtras = rows.stream().map(Installment::extras).reduce(zero, Money::plus);
        Money totalPaid = rows.stream().map(Installment::payment).reduce(zero, Money::plus);
        return new PaymentSchedule(financed, recommended, installmentAmount, extras, rows, totalInterest, totalExtras,
                totalPaid, terms.downPayment().plus(totalPaid), rows.get(rows.size() - 1).dueDate());
    }
}
