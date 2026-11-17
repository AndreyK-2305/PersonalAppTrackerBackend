package com.personaltracker.goals.application.usecase;

import static com.personaltracker.goals.application.usecase.GoalsFakes.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.dto.FinancedGoalCommand;
import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.exception.FinancedGoalNotFoundException;
import com.personaltracker.goals.domain.exception.FinancedRecordNotFoundException;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.model.CreditStatus;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PaymentSchedule.Installment;
import com.personaltracker.goals.domain.model.PrepaymentMode;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pagos, abonos, ajustes de tasa y deshacer, con dobles de los puertos. El crédito es el del ejemplo:
 * $60.000.000 al 18 % EA en 48 cuotas, con la primera el 15 de noviembre de 2025. El reloj marca las
 * 03:00 UTC del 16 de enero de 2026: en Bogotá es 15 de enero (tres cuotas vencidas) y en Tokio, 16.
 */
class CreditActivityTests {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);
    private static final LocalDate FIRST = LocalDate.of(2025, 11, 15);
    private static final FinancedTerms TERMS = new FinancedTerms(cop("60000000"), cop("0"),
            AnnualRate.ofPercent(new BigDecimal("18")), 48, FIRST, null);

    private final GoalsFakes.Module module = new GoalsFakes.Module(Instant.parse("2026-01-16T03:00:00Z"));
    private final UserId owner = UserId.newId();
    private FinancedGoalId goalId;

    @BeforeEach
    void createGoal() {
        goalId = module.financed.create(owner, new FinancedGoalCommand("Carro", TERMS)).goal().id();
    }

    private List<FinancedRecord> stored() {
        return module.financedGoals.records.getOrDefault(goalId, List.of());
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidFinancedRecordException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
    }

    @Test
    void aNewCreditWithPastInstallmentsIsOverdue() {
        FinancedGoalView view = module.financed.get(owner, goalId);

        assertThat(view.status()).isEqualTo(CreditStatus.OVERDUE);
        assertThat(view.plan().nextInstallment().number()).isEqualTo(1);
        assertThat(view.plan().hasRecords()).isFalse();
    }

    @Test
    void payingAnInstallmentRecordsItAndMovesToTheNextOne() {
        FinancedGoalView view =
                module.creditPayments.pay(owner, goalId, JAN_15, cop("1720956"), PrepaymentMode.REDUCE_TERM);

        assertThat(view.plan().paidInstallments()).isEqualTo(1);
        assertThat(view.plan().balance()).isEqualTo(cop("59112349.82"));
        assertThat(view.plan().nextInstallment().number()).isEqualTo(2);
        Installment paid = view.schedule().installments().get(0);
        assertThat(paid.paid()).isTrue();
        assertThat(paid.paidOn()).isEqualTo(JAN_15);
        assertThat(stored()).hasSize(1);
        assertThat(stored().get(0).createdAt()).isEqualTo(module.clock.instant());
    }

    @Test
    void theDateOfAPaymentCannotBeInTheFutureOfTheOwnersTimeZone() {
        UserId inTokyo = UserId.newId();
        module.owners.zones.put(inTokyo, GoalsFakes.TOKYO);
        FinancedGoalId tokyoGoal =
                module.financed.create(inTokyo, new FinancedGoalCommand("Carro", TERMS)).goal().id();
        LocalDate jan16 = JAN_15.plusDays(1);

        assertRule(() -> module.creditPayments.pay(owner, goalId, jan16, cop("1720956"), PrepaymentMode.REDUCE_TERM),
                "goals.financed_record.date_invalid");
        assertRule(() -> module.creditPayments.prepay(owner, goalId, jan16, cop("1000"), PrepaymentMode.REDUCE_TERM),
                "goals.financed_record.date_invalid");
        assertRule(() -> module.creditPayments.pay(owner, goalId, JAN_15.minusYears(100).minusDays(1),
                cop("1720956"), PrepaymentMode.REDUCE_TERM), "goals.financed_record.date_invalid");
        assertThat(module.creditPayments.pay(inTokyo, tokyoGoal, jan16, cop("1720956"), PrepaymentMode.REDUCE_TERM)
                .plan().paidInstallments()).isEqualTo(1);
        assertThat(stored()).isEmpty();
    }

    @Test
    void anInvalidRecordIsNotSaved() {
        assertRule(() -> module.creditPayments.pay(owner, goalId, JAN_15, cop("1000000"), PrepaymentMode.REDUCE_TERM),
                "goals.financed_record.payment_amount_invalid");
        assertRule(() -> module.creditPayments.prepay(owner, goalId, JAN_15, cop("60000000.01"),
                PrepaymentMode.REDUCE_TERM), "goals.financed_record.prepayment_amount_invalid");
        assertRule(() -> module.creditRate.adjust(owner, goalId, AnnualRate.ofPercent(new BigDecimal("20")),
                cop("1000")), "goals.financed_record.actual_installment_invalid");

        assertThat(stored()).isEmpty();
    }

    @Test
    void payingThroughAnInstallmentMarksEveryPendingOneUpToIt() {
        FinancedGoalView view = module.creditPayments.payThrough(owner, goalId, 5);

        assertThat(view.plan().paidInstallments()).isEqualTo(5);
        assertThat(view.plan().nextInstallment().number()).isEqualTo(6);
        assertThat(view.plan().interestSavings()).isEqualTo(cop("0"));
        // Las tres vencidas quedan en su fecha; las dos que aún no vencen, con la fecha de hoy.
        assertThat(stored()).extracting(FinancedRecord::date).containsExactly(FIRST, FIRST.plusMonths(1),
                FIRST.plusMonths(2), JAN_15, JAN_15);
        assertThat(stored()).allSatisfy(record -> assertThat(((InstallmentPayment) record).amount())
                .isEqualTo(cop("1720956")));
        assertThat(view.status()).isEqualTo(CreditStatus.ON_TRACK);

        FinancedGoalView more = module.creditPayments.payThrough(owner, goalId, 7);
        assertThat(more.plan().paidInstallments()).isEqualTo(7);
        assertThat(stored()).hasSize(7);
    }

    @Test
    void payingThroughNeedsAPendingInstallmentOfThePlan() {
        module.creditPayments.payThrough(owner, goalId, 3);

        assertRule(() -> module.creditPayments.payThrough(owner, goalId, 3),
                "goals.financed_record.installment_number_invalid");
        assertRule(() -> module.creditPayments.payThrough(owner, goalId, 49),
                "goals.financed_record.installment_number_invalid");
        assertThat(stored()).hasSize(3);
    }

    @Test
    void payingThroughTheLastInstallmentPaysOffTheCredit() {
        FinancedGoalView view = module.creditPayments.payThrough(owner, goalId, 48);

        assertThat(view.status()).isEqualTo(CreditStatus.PAID_OFF);
        assertThat(view.plan().balance()).isEqualTo(cop("0"));
        assertThat(stored()).hasSize(48);
        assertRule(() -> module.creditPayments.pay(owner, goalId, JAN_15, cop("1"), PrepaymentMode.REDUCE_TERM),
                "goals.financed_record.credit_paid_off");
    }

    @Test
    void aPrepaymentAndARateAdjustmentRecalculateThePlan() {
        module.creditPayments.payThrough(owner, goalId, 12);

        FinancedGoalView prepaid =
                module.creditPayments.prepay(owner, goalId, JAN_15, cop("5000000"), PrepaymentMode.REDUCE_TERM);
        assertThat(prepaid.plan().pendingInstallments()).isEqualTo(32);
        assertThat(prepaid.plan().interestSavings()).isEqualTo(cop("3006724.63"));

        FinancedGoalView adjusted =
                module.creditRate.adjust(owner, goalId, AnnualRate.ofPercent(new BigDecimal("20")), null);
        assertThat(adjusted.plan().currentRate().percent()).isEqualByComparingTo("20");
        assertThat(adjusted.plan().pendingInstallments()).isEqualTo(32);
        assertThat(stored().get(13).date()).isEqualTo(JAN_15);
        assertThat(stored()).extracting(FinancedRecord::kind).endsWith(FinancedRecord.Kind.PREPAYMENT,
                FinancedRecord.Kind.RATE_ADJUSTMENT);
    }

    @Test
    void onlyTheLastRecordCanBeUndone() {
        module.creditPayments.payThrough(owner, goalId, 2);
        FinancedGoalView prepaid =
                module.creditPayments.prepay(owner, goalId, JAN_15, cop("5000000"), PrepaymentMode.REDUCE_TERM);
        UUID firstPayment = stored().get(0).id();
        UUID prepayment = prepaid.plan().lastRecord().id();

        assertRule(() -> module.creditUndo.undo(owner, goalId, firstPayment), "goals.financed_record.not_last");
        assertThatThrownBy(() -> module.creditUndo.undo(owner, goalId, UUID.randomUUID()))
                .isInstanceOf(FinancedRecordNotFoundException.class);
        assertThat(stored()).hasSize(3);

        FinancedGoalView undone = module.creditUndo.undo(owner, goalId, prepayment);

        assertThat(undone.plan().balance()).isEqualTo(module.financed.get(owner, goalId).plan().balance());
        assertThat(undone.plan().interestSavings()).isEqualTo(cop("0"));
        assertThat(undone.plan().paidInstallments()).isEqualTo(2);
        assertThat(stored()).hasSize(2);
    }

    @Test
    void nobodyCanTouchAnotherUsersCredit() {
        module.creditPayments.payThrough(owner, goalId, 1);
        UUID record = stored().get(0).id();
        UserId stranger = UserId.newId();

        assertThatThrownBy(() -> module.creditPayments.pay(stranger, goalId, JAN_15, cop("1720956"),
                PrepaymentMode.REDUCE_TERM)).isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.creditPayments.payThrough(stranger, goalId, 5))
                .isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.creditPayments.prepay(stranger, goalId, JAN_15, cop("1000"),
                PrepaymentMode.REDUCE_TERM)).isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.creditRate.adjust(stranger, goalId,
                AnnualRate.ofPercent(new BigDecimal("20")), null)).isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.creditUndo.undo(stranger, goalId, record))
                .isInstanceOf(FinancedGoalNotFoundException.class);
        assertThat(stored()).hasSize(1);
    }

    @Test
    void withRecordsOnlyTheNameOfTheGoalCanChange() {
        module.creditPayments.payThrough(owner, goalId, 1);
        FinancedTerms otherTerms = new FinancedTerms(cop("60000000"), cop("0"),
                AnnualRate.ofPercent(new BigDecimal("19")), 48, FIRST, null);

        FinancedGoalView renamed = module.financed.update(owner, goalId, new FinancedGoalCommand("Carro rojo", TERMS));
        assertThat(renamed.goal().name()).isEqualTo("Carro rojo");
        assertThat(renamed.plan().paidInstallments()).isEqualTo(1);

        assertThatThrownBy(() -> module.financed.update(owner, goalId, new FinancedGoalCommand("Carro", otherTerms)))
                .isInstanceOfSatisfying(InvalidFinancedGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.financed_goal.terms_locked"));
        assertThat(module.financedGoals.stored.get(goalId).terms()).isEqualTo(TERMS);
        assertThat(module.financedGoals.stored.get(goalId).name()).isEqualTo("Carro rojo");

        // Sin registros, las condiciones vuelven a poder cambiarse.
        module.creditUndo.undo(owner, goalId, stored().get(0).id());
        assertThat(module.financed.update(owner, goalId, new FinancedGoalCommand("Carro", otherTerms)).goal().terms())
                .isEqualTo(otherTerms);
    }

    @Test
    void theListBringsTheCurrentStateOfEachCredit() {
        module.creditPayments.payThrough(owner, goalId, 12);
        module.creditPayments.prepay(owner, goalId, JAN_15, cop("5000000"), PrepaymentMode.REDUCE_TERM);

        FinancedGoalView view = module.financed.list(owner).get(0);

        assertThat(view.plan().paidInstallments()).isEqualTo(12);
        assertThat(view.plan().pendingInstallments()).isEqualTo(32);
        assertThat(view.status()).isEqualTo(CreditStatus.ON_TRACK);
    }

    @Test
    void deletingTheGoalDeletesItsRecords() {
        module.creditPayments.payThrough(owner, goalId, 3);

        module.financed.delete(owner, goalId);

        assertThat(module.financedGoals.records).doesNotContainKey(goalId);
    }
}
