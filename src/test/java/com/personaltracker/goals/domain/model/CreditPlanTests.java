package com.personaltracker.goals.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.goals.domain.model.PaymentSchedule.Installment;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El plan de un crédito tras repasar sus pagos, abonos y ajustes de tasa (ADR-0018). El caso base es
 * el ejemplo del diseño: $60.000.000 al 18 % EA en 48 cuotas de $1.720.956. Los valores esperados se
 * calcularon aparte con 50 dígitos.
 */
class CreditPlanTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final LocalDate FIRST = LocalDate.of(2026, 2, 15);
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final FinancedTerms TERMS = terms(null);

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static AnnualRate percent(String value) {
        return AnnualRate.ofPercent(new BigDecimal(value));
    }

    private static FinancedTerms terms(String actualInstallment) {
        return new FinancedTerms(cop("60000000"), cop("0"), percent("18"), 48, FIRST,
                actualInstallment == null ? null : cop(actualInstallment));
    }

    private static FinancedRecord payment(String amount) {
        return payment(amount, PrepaymentMode.REDUCE_TERM);
    }

    private static FinancedRecord payment(String amount, PrepaymentMode excessMode) {
        return new InstallmentPayment(UUID.randomUUID(), FIRST, cop(amount), excessMode, NOW);
    }

    private static FinancedRecord prepayment(String amount, PrepaymentMode mode) {
        return new Prepayment(UUID.randomUUID(), FIRST, cop(amount), mode, NOW);
    }

    private static FinancedRecord adjustment(String ratePercent, String actualInstallment) {
        return new RateAdjustment(UUID.randomUUID(), FIRST, percent(ratePercent),
                actualInstallment == null ? null : cop(actualInstallment), NOW);
    }

    /** Las primeras {@code count} cuotas pagadas por su valor exacto. */
    private static List<FinancedRecord> paid(FinancedTerms terms, int count) {
        List<FinancedRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Installment next = CreditPlan.of(terms, records).nextInstallment();
            records.add(payment(next.payment().amount().toPlainString()));
        }
        return records;
    }

    private static List<FinancedRecord> with(List<FinancedRecord> records, FinancedRecord... more) {
        List<FinancedRecord> all = new ArrayList<>(records);
        all.addAll(List.of(more));
        return all;
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidFinancedRecordException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
    }

    @Test
    void withoutRecordsThePlanIsTheOriginalOne() {
        CreditPlan plan = CreditPlan.of(TERMS, List.of());

        assertThat(plan.schedule()).isEqualTo(TERMS.schedule());
        assertThat(plan.hasRecords()).isFalse();
        assertThat(plan.lastRecord()).isNull();
        assertThat(plan.balance()).isEqualTo(cop("60000000"));
        assertThat(plan.paidInstallments()).isZero();
        assertThat(plan.pendingInstallments()).isEqualTo(48);
        assertThat(plan.nextInstallment().number()).isEqualTo(1);
        assertThat(plan.paidInterest()).isEqualTo(cop("0"));
        assertThat(plan.pendingInterest()).isEqualTo(cop("22605857.34"));
        assertThat(plan.originalTotalInterest()).isEqualTo(cop("22605857.34"));
        assertThat(plan.interestSavings()).isEqualTo(cop("0"));
        assertThat(plan.paidSoFar()).isEqualTo(cop("0"));
        assertThat(plan.currentRate()).isEqualTo(percent("18"));
    }

    @Test
    void payingInstallmentsOnTimeFollowsTheOriginalPlan() {
        CreditPlan plan = CreditPlan.of(TERMS, paid(TERMS, 12));

        assertThat(plan.paidInstallments()).isEqualTo(12);
        assertThat(plan.pendingInstallments()).isEqualTo(36);
        assertThat(plan.balance()).isEqualTo(cop("48495673.87"));
        assertThat(plan.paidInterest()).isEqualTo(cop("9147145.87"));
        assertThat(plan.paidSoFar()).isEqualTo(cop("20651472"));
        assertThat(plan.interestSavings()).isEqualTo(cop("0"));
        assertThat(plan.nextInstallment().number()).isEqualTo(13);
        assertThat(plan.nextInstallment().dueDate()).isEqualTo(FIRST.plusMonths(12));

        List<Installment> rows = plan.schedule().installments();
        assertThat(rows).hasSize(48);
        assertThat(rows.subList(0, 12)).allMatch(Installment::paid);
        assertThat(rows.subList(12, 48)).noneMatch(Installment::paid);
        // Las cuotas pagadas y las pendientes son las mismas del plan original.
        List<Installment> original = TERMS.schedule().installments();
        for (int i = 0; i < 48; i++) {
            assertThat(rows.get(i).interest()).isEqualTo(original.get(i).interest());
            assertThat(rows.get(i).balance()).isEqualTo(original.get(i).balance());
        }
        assertThat(plan.records()).extracting(CreditPlan.AppliedRecord::installmentNumber)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void aPrepaymentThatReducesTheTermKeepsTheInstallmentAndEndsEarlier() {
        CreditPlan plan = CreditPlan.of(TERMS,
                with(paid(TERMS, 12), prepayment("5000000", PrepaymentMode.REDUCE_TERM)));

        assertThat(plan.balance()).isEqualTo(cop("43495673.87"));
        assertThat(plan.pendingInstallments()).isEqualTo(32);
        assertThat(plan.schedule().installments()).hasSize(44);
        assertThat(plan.schedule().recommendedInstallment()).isEqualTo(cop("1720956"));
        assertThat(plan.nextInstallment().payment()).isEqualTo(cop("1720956"));
        Installment last = plan.schedule().installments().get(43);
        assertThat(last.payment()).isEqualTo(cop("598024.71"));
        assertThat(last.balance()).isEqualTo(cop("0"));
        assertThat(plan.schedule().lastDueDate()).isEqualTo(FIRST.plusMonths(43));
        assertThat(plan.interestSavings()).isEqualTo(cop("3006724.63"));
        assertThat(plan.paidSoFar()).isEqualTo(cop("25651472"));
    }

    @Test
    void aPrepaymentThatReducesTheInstallmentKeepsTheTerm() {
        CreditPlan plan = CreditPlan.of(TERMS,
                with(paid(TERMS, 12), prepayment("5000000", PrepaymentMode.REDUCE_INSTALLMENT)));

        assertThat(plan.pendingInstallments()).isEqualTo(36);
        assertThat(plan.schedule().recommendedInstallment()).isEqualTo(cop("1543522"));
        assertThat(plan.nextInstallment().payment()).isEqualTo(cop("1543522"));
        assertThat(plan.schedule().lastDueDate()).isEqualTo(FIRST.plusMonths(47));
        assertThat(plan.interestSavings()).isEqualTo(cop("1387619.50"));
    }

    @Test
    void aRateAdjustmentRecalculatesTheInstallmentOverTheRemainingTerm() {
        CreditPlan plan = CreditPlan.of(TERMS, with(paid(TERMS, 12), adjustment("20", null)));

        assertThat(plan.currentRate()).isEqualTo(percent("20"));
        assertThat(plan.balance()).isEqualTo(cop("48495673.87"));
        assertThat(plan.pendingInstallments()).isEqualTo(36);
        assertThat(plan.schedule().recommendedInstallment()).isEqualTo(cop("1762283"));
        // Subir la tasa cuesta más: la diferencia frente al plan original es negativa.
        assertThat(plan.interestSavings()).isEqualTo(cop("-1487778.71"));
        assertThat(plan.interestSavings().isNegative()).isTrue();
        // Lo ya pagado no cambia.
        assertThat(plan.paidInterest()).isEqualTo(cop("9147145.87"));
    }

    @Test
    void payingMoreThanTheInstallmentIsTheSameAsPayingItAndPrepayingTheRest() {
        List<FinancedRecord> base = paid(TERMS, 11);
        CreditPlan together = CreditPlan.of(TERMS, with(base, payment("6720956", PrepaymentMode.REDUCE_TERM)));
        CreditPlan separate = CreditPlan.of(TERMS,
                with(base, payment("1720956"), prepayment("5000000", PrepaymentMode.REDUCE_TERM)));

        Installment twelfth = together.schedule().installments().get(11);
        assertThat(twelfth.payment()).isEqualTo(cop("6720956"));
        assertThat(twelfth.extraPrincipal()).isEqualTo(cop("5000000"));
        assertThat(twelfth.balance()).isEqualTo(cop("43495673.87"));
        assertThat(twelfth.interest().plus(twelfth.principal()).plus(twelfth.extras()).plus(twelfth.extraPrincipal()))
                .isEqualTo(twelfth.payment());
        assertThat(together.balance()).isEqualTo(separate.balance());
        assertThat(together.pendingInstallments()).isEqualTo(32);
        assertThat(together.interestSavings()).isEqualTo(separate.interestSavings());
        assertThat(together.paidSoFar()).isEqualTo(separate.paidSoFar());
        assertThat(together.schedule().totalPaid()).isEqualTo(separate.schedule().totalPaid());
    }

    @Test
    void theExcessOfAPaymentCanReduceTheInstallmentInstead() {
        CreditPlan plan = CreditPlan.of(TERMS,
                with(paid(TERMS, 11), payment("6720956", PrepaymentMode.REDUCE_INSTALLMENT)));

        assertThat(plan.pendingInstallments()).isEqualTo(36);
        assertThat(plan.schedule().recommendedInstallment()).isEqualTo(cop("1543522"));
    }

    @Test
    void insuranceAndOthersStayFixedWhenTheInstallmentChanges() {
        FinancedTerms withExtras = terms("1760956");
        List<FinancedRecord> base = paid(withExtras, 12);

        CreditPlan reduced = CreditPlan.of(withExtras,
                with(base, prepayment("5000000", PrepaymentMode.REDUCE_INSTALLMENT)));
        CreditPlan adjusted = CreditPlan.of(withExtras, with(base, adjustment("20", null)));
        CreditPlan adjustedWithNewInstallment = CreditPlan.of(withExtras, with(base, adjustment("20", "1812283")));

        assertThat(reduced.schedule().extrasPerInstallment()).isEqualTo(cop("40000"));
        assertThat(reduced.schedule().installmentAmount()).isEqualTo(cop("1583522"));
        assertThat(adjusted.schedule().extrasPerInstallment()).isEqualTo(cop("40000"));
        assertThat(adjusted.schedule().installmentAmount()).isEqualTo(cop("1802283"));
        assertThat(adjustedWithNewInstallment.schedule().extrasPerInstallment()).isEqualTo(cop("50000"));
        // Las cuotas ya pagadas conservan los seguros que tenían.
        assertThat(adjustedWithNewInstallment.schedule().installments().get(0).extras()).isEqualTo(cop("40000"));
        assertThat(adjustedWithNewInstallment.schedule().installments().get(12).extras()).isEqualTo(cop("50000"));
        assertThat(adjustedWithNewInstallment.schedule().totalExtras()).isEqualTo(cop("2280000"));
    }

    @Test
    void payingEveryInstallmentPaysOffTheCreditWithTheOriginalInterest() {
        CreditPlan plan = CreditPlan.of(TERMS, paid(TERMS, 48));

        assertThat(plan.balance()).isEqualTo(cop("0"));
        assertThat(plan.pendingInstallments()).isZero();
        assertThat(plan.nextInstallment()).isNull();
        assertThat(plan.paidInterest()).isEqualTo(cop("22605857.34"));
        assertThat(plan.interestSavings()).isEqualTo(cop("0"));
        assertThat(plan.paidSoFar()).isEqualTo(cop("82605857.34"));
        assertThat(plan.statusOn(FIRST)).isEqualTo(CreditStatus.PAID_OFF);
    }

    @Test
    void prepayingTheWholeBalancePaysOffTheCredit() {
        CreditPlan plan = CreditPlan.of(TERMS,
                with(paid(TERMS, 12), prepayment("48495673.87", PrepaymentMode.REDUCE_TERM)));

        assertThat(plan.balance()).isEqualTo(cop("0"));
        assertThat(plan.pendingInstallments()).isZero();
        assertThat(plan.schedule().installments()).hasSize(12);
        assertThat(plan.nextInstallment()).isNull();
        // Se ahorran todos los intereses que faltaban.
        assertThat(plan.interestSavings()).isEqualTo(cop("13458711.47"));
        assertThat(plan.schedule().totalPaid()).isEqualTo(cop("69147145.87"));
    }

    @Test
    void aCreditCanBePaidOffBeforeItsFirstInstallment() {
        CreditPlan plan = CreditPlan.of(TERMS, List.of(prepayment("60000000", PrepaymentMode.REDUCE_TERM)));

        assertThat(plan.balance()).isEqualTo(cop("0"));
        assertThat(plan.schedule().installments()).isEmpty();
        assertThat(plan.schedule().lastDueDate()).isEqualTo(FIRST);
        assertThat(plan.interestSavings()).isEqualTo(cop("22605857.34"));
        assertThat(plan.statusOn(FIRST)).isEqualTo(CreditStatus.PAID_OFF);
    }

    @Test
    void theStatusDependsOnTheNextDueDate() {
        CreditPlan plan = CreditPlan.of(TERMS, paid(TERMS, 2));
        LocalDate thirdDueDate = FIRST.plusMonths(2);

        assertThat(plan.statusOn(thirdDueDate.minusDays(1))).isEqualTo(CreditStatus.ON_TRACK);
        assertThat(plan.statusOn(thirdDueDate)).isEqualTo(CreditStatus.ON_TRACK);
        assertThat(plan.statusOn(thirdDueDate.plusDays(1))).isEqualTo(CreditStatus.OVERDUE);
    }

    @Test
    void everyRecordRuleHasItsOwnCode() {
        List<FinancedRecord> base = paid(TERMS, 12);

        assertRule(() -> CreditPlan.of(TERMS, with(base, payment("1720955.99"))),
                "goals.financed_record.payment_amount_invalid");
        // Cuota de $1.720.956 más todo el saldo que queda tras pagarla, y un centavo más.
        assertRule(() -> CreditPlan.of(TERMS, with(base, payment("49169202.67"))),
                "goals.financed_record.payment_amount_invalid");
        assertRule(() -> CreditPlan.of(TERMS, with(base, prepayment("48495673.88", PrepaymentMode.REDUCE_TERM))),
                "goals.financed_record.prepayment_amount_invalid");
        assertRule(() -> CreditPlan.of(TERMS, with(base, adjustment("20", "1762282.99"))),
                "goals.financed_record.actual_installment_invalid");
        assertRule(() -> payment("0"), "goals.financed_record.payment_amount_invalid");
        assertRule(() -> prepayment("0", PrepaymentMode.REDUCE_TERM),
                "goals.financed_record.prepayment_amount_invalid");
        assertRule(() -> CreditPlan.of(TERMS, List.of(new Prepayment(UUID.randomUUID(), FIRST,
                new Money(BigDecimal.TEN, Currency.getInstance("USD")), PrepaymentMode.REDUCE_TERM, NOW))),
                "goals.financed_record.prepayment_amount_invalid");
    }

    @Test
    void aPaymentCanCoverTheInstallmentAndTheWholeRemainingBalance() {
        // Tras 12 cuotas, la 13 abona $1.047.427,21 de capital: quedan $47.448.246,66 por pagar.
        CreditPlan plan = CreditPlan.of(TERMS, with(paid(TERMS, 12), payment("49169202.66")));

        assertThat(plan.balance()).isEqualTo(cop("0"));
        assertThat(plan.paidInstallments()).isEqualTo(13);
        assertThat(plan.pendingInstallments()).isZero();
        assertThat(plan.schedule().installments().get(12).extraPrincipal()).isEqualTo(cop("47448246.66"));
    }

    @Test
    void aPaidOffCreditAcceptsNoMoreRecords() {
        List<FinancedRecord> paidOff = with(paid(TERMS, 12), prepayment("48495673.87", PrepaymentMode.REDUCE_TERM));

        assertRule(() -> CreditPlan.of(TERMS, with(paidOff, payment("1720956"))),
                "goals.financed_record.credit_paid_off");
        assertRule(() -> CreditPlan.of(TERMS, with(paidOff, prepayment("1", PrepaymentMode.REDUCE_TERM))),
                "goals.financed_record.credit_paid_off");
        assertRule(() -> CreditPlan.of(TERMS, with(paidOff, adjustment("20", null))),
                "goals.financed_record.credit_paid_off");
    }

    /** Pase lo que pase en el camino, todo el capital se paga una sola vez y el saldo termina en cero. */
    @Test
    void aMixOfRecordsAlwaysAddsUpToTheFinancedAmount() {
        List<FinancedRecord> records = new ArrayList<>(paid(TERMS, 5));
        records.add(prepayment("3000000", PrepaymentMode.REDUCE_INSTALLMENT));
        records.add(adjustment("22.5", null));
        records.addAll(paidAfter(records, 7));
        records.add(payment(next(records).plus(cop("2500000")).amount().toPlainString(), PrepaymentMode.REDUCE_TERM));
        records.add(adjustment("15", null));
        records.add(prepayment("1000000", PrepaymentMode.REDUCE_TERM));
        CreditPlan midway = CreditPlan.of(TERMS, records);
        records.addAll(paidAfter(records, midway.pendingInstallments()));

        CreditPlan plan = CreditPlan.of(TERMS, records);

        assertThat(plan.balance()).isEqualTo(cop("0"));
        assertThat(plan.pendingInstallments()).isZero();
        Money principal = plan.schedule().installments().stream()
                .map(row -> row.principal().plus(row.extraPrincipal()))
                .reduce(cop("0"), Money::plus)
                .plus(cop("4000000"));
        assertThat(principal).isEqualTo(cop("60000000"));
        assertThat(plan.schedule().totalPaid()).isEqualTo(cop("60000000").plus(plan.paidInterest()));
        assertThat(plan.originalTotalInterest().minus(plan.paidInterest())).isEqualTo(plan.interestSavings());
        Money previous = cop("60000000");
        for (Installment row : plan.schedule().installments()) {
            assertThat(row.balance().amount()).isLessThanOrEqualTo(previous.amount());
            assertThat(row.interest().plus(row.principal()).plus(row.extras()).plus(row.extraPrincipal()))
                    .isEqualTo(row.payment());
            previous = row.balance();
        }
    }

    private static Money next(List<FinancedRecord> records) {
        return CreditPlan.of(TERMS, records).nextInstallment().payment();
    }

    private static List<FinancedRecord> paidAfter(List<FinancedRecord> records, int count) {
        List<FinancedRecord> all = new ArrayList<>(records);
        List<FinancedRecord> added = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FinancedRecord payment = payment(next(all).amount().toPlainString());
            all.add(payment);
            added.add(payment);
        }
        return added;
    }
}
