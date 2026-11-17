package com.personaltracker.goals.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.model.PaymentSchedule.Installment;
import com.personaltracker.shared.domain.InvalidMoneyAmountException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Condiciones de una financiación, su plan de pagos y el agregado {@link FinancedGoal} (ADR-0018). */
class FinancedTermsTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final LocalDate FIRST = LocalDate.of(2026, 2, 15);
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static AnnualRate percent(String value) {
        return AnnualRate.ofPercent(new BigDecimal(value));
    }

    private static FinancedTerms terms(String asset, String down, String rate, int installments, String actual) {
        return new FinancedTerms(cop(asset), cop(down), percent(rate), installments, FIRST,
                actual == null ? null : cop(actual));
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidFinancedGoalException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
    }

    @Test
    void financesTheAssetValueMinusTheDownPayment() {
        FinancedTerms terms = terms("70000000", "10000000", "18", 48, null);

        assertThat(terms.financedAmount()).isEqualTo(cop("60000000"));
        assertThat(terms.recommendedInstallment()).isEqualTo(cop("1720956"));
    }

    @Test
    void theScheduleUsesTheRecommendedInstallmentWhenThereIsNoActualOne() {
        PaymentSchedule schedule = terms("60000000", "0", "18", 48, null).schedule();

        assertThat(schedule.installmentAmount()).isEqualTo(cop("1720956"));
        assertThat(schedule.extrasPerInstallment()).isEqualTo(cop("0"));
        assertThat(schedule.installments()).hasSize(48);
        assertThat(schedule.installments().get(0)).isEqualTo(new Installment(1, FIRST, cop("1720956"),
                cop("833305.82"), cop("887650.18"), cop("0"), cop("0"), cop("59112349.82"), null));
        assertThat(schedule.installments()).noneMatch(Installment::paid);
        assertThat(schedule.totalInterest()).isEqualTo(cop("22605857.34"));
        assertThat(schedule.totalExtras()).isEqualTo(cop("0"));
        assertThat(schedule.totalPaid()).isEqualTo(cop("82605857.34"));
        assertThat(schedule.totalCost()).isEqualTo(cop("82605857.34"));
        assertThat(schedule.lastDueDate()).isEqualTo(LocalDate.of(2030, 1, 15));
    }

    @Test
    void theDifferenceWithTheActualInstallmentIsInsuranceAndOthers() {
        PaymentSchedule schedule = terms("70000000", "10000000", "18", 48, "1760956").schedule();

        assertThat(schedule.recommendedInstallment()).isEqualTo(cop("1720956"));
        assertThat(schedule.installmentAmount()).isEqualTo(cop("1760956"));
        assertThat(schedule.extrasPerInstallment()).isEqualTo(cop("40000"));
        Installment first = schedule.installments().get(0);
        assertThat(first.payment()).isEqualTo(cop("1760956"));
        assertThat(first.extras()).isEqualTo(cop("40000"));
        assertThat(first.interest().plus(first.principal()).plus(first.extras())).isEqualTo(first.payment());
        // Los seguros no amortizan: capital, interés y saldo son los mismos que sin cuota real.
        assertThat(first.principal()).isEqualTo(cop("887650.18"));
        assertThat(schedule.totalInterest()).isEqualTo(cop("22605857.34"));
        assertThat(schedule.totalExtras()).isEqualTo(cop("1920000"));
        assertThat(schedule.totalPaid()).isEqualTo(cop("84525857.34"));
        assertThat(schedule.totalCost()).isEqualTo(cop("94525857.34"));
        assertThat(schedule.installments().get(47).payment()).isEqualTo(cop("1760925.34"));
    }

    @Test
    void theInstallmentsAreDueOnTheSameDayEachMonth() {
        FinancedTerms endOfMonth = new FinancedTerms(cop("1200000"), cop("0"), percent("0"), 4,
                LocalDate.of(2027, 1, 31), null);

        assertThat(endOfMonth.schedule().installments()).extracting(Installment::dueDate).containsExactly(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30));
        assertThat(endOfMonth.schedule().lastDueDate()).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    @Test
    void anActualInstallmentEqualToTheRecommendedOneIsAccepted() {
        assertThatCode(() -> terms("60000000", "0", "18", 48, "1720956")).doesNotThrowAnyException();
    }

    @Test
    void everyRuleHasItsOwnStableCode() {
        assertRule(() -> terms("0", "0", "18", 48, null), "goals.financed_goal.asset_value_invalid");
        assertRule(() -> terms("1000", "1000", "18", 48, null), "goals.financed_goal.down_payment_invalid");
        assertRule(() -> terms("1000", "1500", "18", 48, null), "goals.financed_goal.down_payment_invalid");
        assertRule(() -> new FinancedTerms(cop("1000"), new Money(BigDecimal.ONE, Currency.getInstance("USD")),
                percent("18"), 12, FIRST, null), "goals.financed_goal.down_payment_invalid");
        assertRule(() -> terms("1000", "0", "18", 0, null), "goals.financed_goal.installments_invalid");
        assertRule(() -> terms("1000", "0", "18", 601, null), "goals.financed_goal.installments_invalid");
        assertRule(() -> terms("60000000", "0", "18", 48, "1720955.99"),
                "goals.financed_goal.actual_installment_invalid");
        assertRule(() -> FinancedGoal.create(FinancedGoalId.newId(), UserId.newId(), " ",
                terms("1000", "0", "18", 12, null), NOW), "goals.financed_goal.name_invalid");
    }

    @Test
    void theLimitsOfTheTermAreAccepted() {
        assertThat(terms("1000", "0", "18", 1, null).schedule().installments()).hasSize(1);
        assertThat(terms("60000000", "0", "18", 600, null).schedule().installments()).hasSize(600);
        assertThat(terms("1000", "999.99", "18", 12, null).financedAmount()).isEqualTo(cop("0.01"));
    }

    @Test
    void theFirstDueDateMustBeWithinAHundredYearsOfToday() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        FinancedTerms past = new FinancedTerms(cop("1000"), cop("0"), percent("18"), 12, today.minusYears(100), null);
        FinancedTerms future = new FinancedTerms(cop("1000"), cop("0"), percent("18"), 12, today.plusYears(100), null);
        FinancedTerms tooOld = new FinancedTerms(cop("1000"), cop("0"), percent("18"), 12,
                today.minusYears(100).minusDays(1), null);
        FinancedTerms tooFar = new FinancedTerms(cop("1000"), cop("0"), percent("18"), 12, LocalDate.MAX.minusYears(60),
                null);

        assertThatCode(() -> past.requireFirstDueDateNear(today)).doesNotThrowAnyException();
        assertThatCode(() -> future.requireFirstDueDateNear(today)).doesNotThrowAnyException();
        assertRule(() -> tooOld.requireFirstDueDateNear(today), "goals.financed_goal.first_due_date_invalid");
        assertRule(() -> tooFar.requireFirstDueDateNear(today), "goals.financed_goal.first_due_date_invalid");
    }

    /** Un plan cuyos totales no caben en un monto no llega a existir: falla al calcularlo, siempre igual. */
    @Test
    void aPlanWhoseTotalsDoNotFitInAnAmountCannotBeBuilt() {
        FinancedTerms huge = terms("99999999999999999.99", "0", "1000", 600, null);

        assertThatThrownBy(huge::schedule).isInstanceOf(InvalidMoneyAmountException.class);
        assertThatThrownBy(() -> terms("99999999999999999.99", "0", "1000", 1, null).recommendedInstallment())
                .isInstanceOf(InvalidMoneyAmountException.class);
    }

    @Test
    void tinyAmountsEndEarlyWithoutNegativeNumbers() {
        PaymentSchedule schedule = terms("10", "0", "0", 600, null).schedule();

        assertThat(schedule.installments()).hasSize(10);
        assertThat(schedule.totalPaid()).isEqualTo(cop("10"));
        assertThat(schedule.lastDueDate()).isEqualTo(FIRST.plusMonths(9));
    }

    @Test
    void theGoalTrimsItsNameAndUpdatesAtomically() {
        FinancedTerms terms = terms("1000", "0", "18", 12, null);
        FinancedGoal goal = FinancedGoal.create(FinancedGoalId.newId(), UserId.newId(), "  Carro  ", terms, NOW);
        FinancedTerms newTerms = terms("2000", "0", "18", 24, null);

        assertThat(goal.name()).isEqualTo("Carro");
        assertThatThrownBy(() -> goal.update("", newTerms, NOW.plusSeconds(60)))
                .isInstanceOf(InvalidFinancedGoalException.class);
        assertThat(goal.terms()).isEqualTo(terms);
        assertThat(goal.updatedAt()).isEqualTo(NOW);

        goal.update(" Moto ", newTerms, NOW.plusSeconds(60));
        assertThat(goal.name()).isEqualTo("Moto");
        assertThat(goal.terms()).isEqualTo(newTerms);
        assertThat(goal.createdAt()).isEqualTo(NOW);
        assertThat(goal.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }
}
