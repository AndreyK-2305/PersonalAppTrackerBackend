package com.personaltracker.goals.application.usecase;

import static com.personaltracker.goals.application.usecase.GoalsFakes.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.dto.FinancedGoalCommand;
import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.exception.FinancedGoalNotFoundException;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PaymentSchedule;
import com.personaltracker.shared.domain.InvalidMoneyAmountException;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Casos de uso de metas financiadas con dobles de los puertos. El reloj marca las 03:00 UTC del 16
 * de enero: en Bogotá todavía es 15 de enero y en Tokio ya es 16.
 */
class ManageFinancedGoalsTests {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    private final GoalsFakes.Module module = new GoalsFakes.Module(Instant.parse("2026-01-16T03:00:00Z"));
    private final UserId owner = UserId.newId();
    private final UserId stranger = UserId.newId();

    private static FinancedTerms terms(String asset, LocalDate firstDueDate) {
        return new FinancedTerms(cop(asset), cop("0"), AnnualRate.ofPercent(new BigDecimal("18")), 48, firstDueDate,
                null);
    }

    private static FinancedGoalCommand command(String name, LocalDate firstDueDate) {
        return new FinancedGoalCommand(name, terms("60000000", firstDueDate));
    }

    @Test
    void createsTheGoalAndReturnsItsPlan() {
        FinancedGoalView view = module.financed.create(owner, command(" Carro ", JAN_15.plusMonths(1)));

        assertThat(view.goal().name()).isEqualTo("Carro");
        assertThat(view.goal().owner()).isEqualTo(owner);
        assertThat(view.goal().createdAt()).isEqualTo(module.clock.instant());
        assertThat(view.schedule().recommendedInstallment()).isEqualTo(cop("1720956"));
        assertThat(view.schedule().installments()).hasSize(48);
        assertThat(module.financedGoals.stored).containsOnlyKeys(view.goal().id());
    }

    @Test
    void theFirstDueDateIsCheckedAgainstTodayInTheOwnersTimeZone() {
        UserId inTokyo = UserId.newId();
        module.owners.zones.put(inTokyo, GoalsFakes.TOKYO);
        LocalDate limitInBogota = JAN_15.plusYears(100);

        module.financed.create(owner, command("Casa", limitInBogota));
        module.financed.create(inTokyo, command("Casa", limitInBogota.plusDays(1)));

        assertThatThrownBy(() -> module.financed.create(owner, command("Casa", limitInBogota.plusDays(1))))
                .isInstanceOfSatisfying(InvalidFinancedGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.financed_goal.first_due_date_invalid"));
        assertThatThrownBy(() -> module.financed.create(owner, command("Casa", JAN_15.minusYears(100).minusDays(1))))
                .isInstanceOf(InvalidFinancedGoalException.class);
        assertThat(module.financedGoals.stored).hasSize(2);
    }

    @Test
    void aCreditThatAlreadyStartedCanBeRegistered() {
        FinancedGoalView view = module.financed.create(owner, command("Carro", JAN_15.minusYears(2)));

        assertThat(view.schedule().installments().get(0).dueDate()).isEqualTo(JAN_15.minusYears(2));
        assertThat(view.schedule().lastDueDate()).isEqualTo(JAN_15.minusYears(2).plusMonths(47));
    }

    @Test
    void aRejectedCreationSavesNothing() {
        assertThatThrownBy(() -> module.financed.create(owner, command("  ", JAN_15)))
                .isInstanceOf(InvalidFinancedGoalException.class);
        FinancedTerms huge = new FinancedTerms(cop("99999999999999999.99"), cop("0"),
                AnnualRate.ofPercent(new BigDecimal("1000")), 600, JAN_15, null);
        assertThatThrownBy(() -> module.financed.create(owner, new FinancedGoalCommand("Imposible", huge)))
                .isInstanceOf(InvalidMoneyAmountException.class);

        assertThat(module.financedGoals.stored).isEmpty();
    }

    @Test
    void listsOnlyTheOwnersGoalsNewestFirstWithTheirPlans() {
        module.financed.create(owner, command("Primera", JAN_15));
        module.clock.advanceDays(1);
        module.financed.create(owner, command("Segunda", JAN_15));
        module.financed.create(stranger, command("Ajena", JAN_15));

        assertThat(module.financed.list(owner)).extracting(view -> view.goal().name())
                .containsExactly("Segunda", "Primera");
        assertThat(module.financed.list(owner))
                .allSatisfy(view -> assertThat(view.schedule().totalInterest()).isEqualTo(cop("22605857.34")));
        assertThat(module.financed.list(UserId.newId())).isEmpty();
    }

    @Test
    void anotherUsersGoalDoesNotExist() {
        FinancedGoal foreign = module.financed.create(stranger, command("Ajena", JAN_15)).goal();

        assertThatThrownBy(() -> module.financed.get(owner, foreign.id()))
                .isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.financed.update(owner, foreign.id(), command("Mía", JAN_15)))
                .isInstanceOf(FinancedGoalNotFoundException.class);
        assertThatThrownBy(() -> module.financed.delete(owner, foreign.id()))
                .isInstanceOf(FinancedGoalNotFoundException.class);

        assertThat(module.financedGoals.stored.get(foreign.id()).name()).isEqualTo("Ajena");
    }

    @Test
    void updatingReplacesTheTermsAndRecalculatesThePlan() {
        FinancedGoal goal = module.financed.create(owner, command("Carro", JAN_15)).goal();
        module.clock.advanceDays(3);
        FinancedTerms newTerms = new FinancedTerms(cop("70000000"), cop("10000000"),
                AnnualRate.ofPercent(new BigDecimal("18")), 48, JAN_15, cop("1760956"));

        FinancedGoalView updated =
                module.financed.update(owner, goal.id(), new FinancedGoalCommand("Carro nuevo", newTerms));

        PaymentSchedule schedule = updated.schedule();
        assertThat(updated.goal().name()).isEqualTo("Carro nuevo");
        assertThat(schedule.extrasPerInstallment()).isEqualTo(cop("40000"));
        assertThat(schedule.totalCost()).isEqualTo(cop("94525857.34"));
        assertThat(updated.goal().updatedAt()).isEqualTo(module.clock.instant());
        assertThat(updated.goal().createdAt()).isBefore(updated.goal().updatedAt());
    }

    /** Una meta vieja puede editarse sin tocar su fecha, aunque esa fecha ya no pasaría la regla. */
    @Test
    void theFirstDueDateIsOnlyCheckedAgainWhenItChanges() {
        LocalDate oldDate = JAN_15.minusYears(100);
        FinancedGoal goal = module.financed.create(owner, command("Casa", oldDate)).goal();
        module.clock.advanceDays(400);

        FinancedGoalView renamed = module.financed.update(owner, goal.id(), command("Casa vieja", oldDate));
        assertThat(renamed.goal().name()).isEqualTo("Casa vieja");

        assertThatThrownBy(() -> module.financed.update(owner, goal.id(), command("Casa", oldDate.minusDays(1))))
                .isInstanceOf(InvalidFinancedGoalException.class);
        assertThat(module.financedGoals.stored.get(goal.id()).terms().firstDueDate()).isEqualTo(oldDate);
    }

    @Test
    void anInvalidEditLeavesTheGoalUntouched() {
        FinancedGoal goal = module.financed.create(owner, command("Carro", JAN_15)).goal();

        assertThatThrownBy(() -> module.financed.update(owner, goal.id(), command("", JAN_15)))
                .isInstanceOf(InvalidFinancedGoalException.class);

        assertThat(module.financedGoals.stored.get(goal.id()).name()).isEqualTo("Carro");
    }

    @Test
    void deletesOnlyThatGoal() {
        FinancedGoal goal = module.financed.create(owner, command("Carro", JAN_15)).goal();
        FinancedGoal other = module.financed.create(owner, command("Casa", JAN_15)).goal();

        module.financed.delete(owner, goal.id());

        assertThat(module.financedGoals.stored).containsOnlyKeys(other.id());
    }

    @Test
    void simulatingAppliesTheSameRulesAndSavesNothing() {
        PaymentSchedule schedule = module.simulation.simulate(owner, terms("60000000", JAN_15));

        assertThat(schedule.recommendedInstallment()).isEqualTo(cop("1720956"));
        assertThat(schedule.installments()).hasSize(48);
        assertThatThrownBy(() -> module.simulation.simulate(owner, terms("60000000", JAN_15.plusYears(101))))
                .isInstanceOf(InvalidFinancedGoalException.class);
        assertThat(module.financedGoals.saves).isZero();
        assertThat(module.financedGoals.stored).isEmpty();
    }
}
