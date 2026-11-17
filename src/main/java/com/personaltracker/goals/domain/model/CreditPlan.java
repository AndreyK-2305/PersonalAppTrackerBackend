package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException.Rule;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.goals.domain.model.PaymentSchedule.Installment;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.Amortization;
import com.personaltracker.shared.domain.finance.AnnualRate;
import com.personaltracker.shared.domain.finance.FinancialMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Estado de un crédito (ADR-0018): el plan de pagos que resulta de repasar, en orden, sus pagos,
 * abonos y ajustes de tasa sobre las condiciones originales. Es un cálculo puro: las mismas
 * condiciones y los mismos registros dan siempre el mismo plan, y un registro que incumple una regla
 * hace fallar el cálculo.
 *
 * @param schedule plan actual, con las cuotas pagadas y las pendientes
 * @param records los registros en orden, con la cuota a la que corresponde cada pago
 * @param nextInstallment la siguiente cuota pendiente, o {@code null} si el crédito está pagado
 * @param interestSavings intereses del plan original menos los del plan actual; negativo si ahora se
 *     pagan más, por ejemplo tras una subida de tasa
 * @param paidSoFar suma de las cuotas pagadas y de los abonos
 */
public record CreditPlan(
        PaymentSchedule schedule,
        List<AppliedRecord> records,
        Money balance,
        AnnualRate currentRate,
        int paidInstallments,
        int pendingInstallments,
        Installment nextInstallment,
        Money paidInterest,
        Money pendingInterest,
        Money originalTotalInterest,
        Money interestSavings,
        Money paidSoFar) {

    public CreditPlan {
        records = List.copyOf(records);
    }

    /** Un registro y, si es un pago, el número de la cuota que pagó. */
    public record AppliedRecord(FinancedRecord record, Integer installmentNumber) {}

    public static CreditPlan of(FinancedTerms terms, List<FinancedRecord> records) {
        Objects.requireNonNull(terms, "terms");
        Replay replay = new Replay(terms);
        records.forEach(replay::apply);
        return replay.result(records.isEmpty());
    }

    public boolean hasRecords() {
        return !records.isEmpty();
    }

    /** El último registro, que es el único que se puede deshacer; {@code null} si no hay ninguno. */
    public FinancedRecord lastRecord() {
        return records.isEmpty() ? null : records.get(records.size() - 1).record();
    }

    public CreditStatus statusOn(LocalDate today) {
        if (nextInstallment == null) {
            return CreditStatus.PAID_OFF;
        }
        return nextInstallment.dueDate().isBefore(today) ? CreditStatus.OVERDUE : CreditStatus.ON_TRACK;
    }

    /** Repasa los registros sobre las condiciones originales, llevando el saldo, la tasa y la cuota. */
    private static final class Replay {

        private final FinancedTerms terms;
        private final Currency currency;
        private final List<Installment> paid = new ArrayList<>();
        private final List<AppliedRecord> applied = new ArrayList<>();
        private BigDecimal balance;
        private AnnualRate rate;
        private BigDecimal periodRate;
        /** Cuota calculada vigente, sin seguros y otros. */
        private BigDecimal installment;
        private Money extras;
        /** Cuotas que faltan según el plan vigente. */
        private int remaining;
        private Money standalonePrepayments;

        Replay(FinancedTerms terms) {
            this.terms = terms;
            this.currency = terms.assetValue().currency();
            this.balance = terms.financedAmount().amount();
            this.rate = terms.rate();
            this.periodRate = rate.monthly();
            Money recommended = terms.recommendedInstallment();
            this.installment = recommended.amount();
            this.extras = terms.actualInstallment() == null ? Money.zero(currency)
                    : terms.actualInstallment().minus(recommended);
            this.remaining = terms.installments();
            this.standalonePrepayments = Money.zero(currency);
        }

        void apply(FinancedRecord record) {
            if (balance.signum() == 0) {
                throw new InvalidFinancedRecordException(Rule.PAID_OFF, "El crédito ya está pagado.");
            }
            switch (record) {
                case InstallmentPayment payment -> pay(payment);
                case Prepayment prepayment -> prepay(prepayment);
                case RateAdjustment adjustment -> adjust(adjustment);
            }
        }

        private void pay(InstallmentPayment payment) {
            requireCurrency(payment.amount(), Rule.PAYMENT_AMOUNT);
            Amortization.Row row = Amortization.nextRow(balance, periodRate, remaining, installment);
            Money scheduled = money(row.payment()).plus(extras);
            if (payment.amount().amount().compareTo(scheduled.amount()) < 0) {
                throw new InvalidFinancedRecordException(Rule.PAYMENT_AMOUNT,
                        "El pago no puede ser menor que el valor de la cuota.");
            }
            BigDecimal excess = payment.amount().amount().subtract(scheduled.amount());
            if (excess.compareTo(row.balance()) > 0) {
                throw new InvalidFinancedRecordException(Rule.PAYMENT_AMOUNT,
                        "El pago supera lo que falta por pagar del crédito.");
            }
            int number = paid.size() + 1;
            Money paidExtras = extras;
            balance = row.balance();
            remaining -= 1;
            if (excess.signum() > 0) {
                reduceBalance(excess, payment.excessMode());
            } else if (balance.signum() == 0) {
                remaining = 0;
            }
            paid.add(new Installment(number, dueDate(number), payment.amount(), money(row.interest()),
                    money(row.principal()), paidExtras, money(excess), money(balance), payment.date()));
            applied.add(new AppliedRecord(payment, number));
        }

        private void prepay(Prepayment prepayment) {
            requireCurrency(prepayment.amount(), Rule.PREPAYMENT_AMOUNT);
            if (prepayment.amount().amount().compareTo(balance) > 0) {
                throw new InvalidFinancedRecordException(Rule.PREPAYMENT_AMOUNT,
                        "El abono no puede ser mayor que el saldo del crédito.");
            }
            reduceBalance(prepayment.amount().amount(), prepayment.mode());
            standalonePrepayments = standalonePrepayments.plus(prepayment.amount());
            applied.add(new AppliedRecord(prepayment, null));
        }

        private void adjust(RateAdjustment adjustment) {
            rate = adjustment.rate();
            periodRate = rate.monthly();
            installment = recalculatedInstallment();
            if (adjustment.actualInstallment() != null) {
                Money actual = adjustment.actualInstallment();
                if (!actual.currency().equals(currency) || actual.amount().compareTo(installment) < 0) {
                    throw new InvalidFinancedRecordException(Rule.ACTUAL_INSTALLMENT,
                            "La cuota real no puede ser menor que la cuota calculada con la nueva tasa.");
                }
                extras = actual.minus(money(installment));
            }
            applied.add(new AppliedRecord(adjustment, null));
        }

        /** Baja el saldo con un abono a capital y ajusta el plazo o la cuota según el modo. */
        private void reduceBalance(BigDecimal amount, PrepaymentMode mode) {
            balance = balance.subtract(amount);
            if (balance.signum() == 0) {
                remaining = 0;
            } else if (mode == PrepaymentMode.REDUCE_INSTALLMENT) {
                installment = recalculatedInstallment();
            } else {
                remaining = Amortization.schedule(balance, periodRate, remaining, installment).size();
            }
        }

        /** Cuota fija sobre el saldo y las cuotas que faltan, redondeada hacia arriba al peso. */
        private BigDecimal recalculatedInstallment() {
            return money(FinancialMath.ceilToUnit(Amortization.fixedInstallment(balance, periodRate, remaining)))
                    .amount();
        }

        CreditPlan result(boolean original) {
            List<Installment> pending = new ArrayList<>();
            if (balance.signum() > 0) {
                for (Amortization.Row row : Amortization.schedule(balance, periodRate, remaining, installment)) {
                    int number = paid.size() + row.number();
                    pending.add(new Installment(number, dueDate(number), money(row.payment()).plus(extras),
                            money(row.interest()), money(row.principal()), extras, Money.zero(currency),
                            money(row.balance()), null));
                }
            }
            List<Installment> all = new ArrayList<>(paid);
            all.addAll(pending);

            Money paidInterest = sum(paid, Installment::interest);
            Money pendingInterest = sum(pending, Installment::interest);
            Money totalInterest = paidInterest.plus(pendingInterest);
            Money paidSoFar = sum(paid, Installment::payment).plus(standalonePrepayments);
            Money totalPaid = paidSoFar.plus(sum(pending, Installment::payment));
            Money recommended = money(installment);
            PaymentSchedule schedule = new PaymentSchedule(terms.financedAmount(), recommended,
                    recommended.plus(extras), extras, all, totalInterest, sum(all, Installment::extras), totalPaid,
                    terms.downPayment().plus(totalPaid),
                    all.isEmpty() ? terms.firstDueDate() : all.get(all.size() - 1).dueDate());

            Money originalInterest = original ? totalInterest
                    : CreditPlan.of(terms, List.of()).schedule().totalInterest();
            return new CreditPlan(schedule, applied, money(balance), rate, paid.size(), pending.size(),
                    pending.isEmpty() ? null : pending.get(0), paidInterest, pendingInterest, originalInterest,
                    originalInterest.minus(totalInterest), paidSoFar);
        }

        private LocalDate dueDate(int number) {
            return terms.firstDueDate().plusMonths(number - 1L);
        }

        private Money money(BigDecimal amount) {
            return new Money(amount, currency);
        }

        private Money sum(List<Installment> rows, java.util.function.Function<Installment, Money> value) {
            return rows.stream().map(value).reduce(Money.zero(currency), Money::plus);
        }

        private void requireCurrency(Money amount, Rule rule) {
            if (!amount.currency().equals(currency)) {
                throw new InvalidFinancedRecordException(rule, "El valor debe estar en la moneda del crédito.");
            }
        }
    }
}
