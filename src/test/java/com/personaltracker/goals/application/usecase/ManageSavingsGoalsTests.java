package com.personaltracker.goals.application.usecase;

import static com.personaltracker.goals.application.usecase.GoalsFakes.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.dto.SavingsGoalCommand;
import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException;
import com.personaltracker.goals.domain.exception.SavingsGoalNotFoundException;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Casos de uso de metas de ahorro con dobles de los puertos. El reloj marca las 03:00 UTC del 16 de
 * enero: en Bogotá todavía es 15 de enero y en Tokio ya es 16.
 */
class ManageSavingsGoalsTests {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);
    private static final LocalDate JAN_16 = LocalDate.of(2026, 1, 16);

    private final GoalsFakes.Module module = new GoalsFakes.Module(Instant.parse("2026-01-16T03:00:00Z"));
    private final UserId owner = UserId.newId();
    private final UserId stranger = UserId.newId();

    private static SavingsGoalCommand byDate(String name, String target, LocalDate date) {
        return new SavingsGoalCommand(name, cop(target), new SavingsPlan.ByTargetDate(date));
    }

    @Test
    void theTargetDateMustBeAfterTodayInTheOwnersTimeZone() {
        UserId inTokyo = UserId.newId();
        module.owners.zones.put(inTokyo, GoalsFakes.TOKYO);

        SavingsGoalView created = module.manage.create(owner, byDate("Consola", "1200000", JAN_16));

        assertThat(created.projection().overdue()).isFalse();
        assertThatThrownBy(() -> module.manage.create(owner, byDate("Consola", "1200000", JAN_15)))
                .isInstanceOf(InvalidSavingsGoalException.class);
        assertThatThrownBy(() -> module.manage.create(inTokyo, byDate("Consola", "1200000", JAN_16)))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void theTargetDateCannotBeMoreThanAHundredYearsAway() {
        SavingsGoalView atTheLimit = module.manage.create(owner, byDate("Lejana", "1000", JAN_15.plusYears(100)));

        assertThat(atTheLimit.projection().monthsRemaining()).isEqualTo(1200);
        assertThatThrownBy(() -> module.manage.create(owner, byDate("Lejana", "1000", JAN_15.plusYears(100).plusDays(1))))
                .isInstanceOfSatisfying(InvalidSavingsGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.savings_goal.target_date_invalid"));
        assertThatThrownBy(() -> module.manage.create(owner, byDate("Imposible", "1000", LocalDate.MAX)))
                .isInstanceOf(InvalidSavingsGoalException.class);
        assertThatThrownBy(() -> module.manage.update(owner, atTheLimit.goal().id(),
                byDate("Lejana", "1000", JAN_15.plusYears(150)))).isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void aRejectedCreationSavesNothing() {
        assertThatThrownBy(() -> module.manage.create(owner, byDate(" ", "1200000", JAN_16)))
                .isInstanceOf(InvalidSavingsGoalException.class);
        assertThatThrownBy(() -> module.manage.create(owner, byDate("Consola", "0", JAN_16)))
                .isInstanceOf(InvalidSavingsGoalException.class);

        assertThat(module.goals.stored).isEmpty();
    }

    @Test
    void listsOnlyTheOwnersGoalsNewestFirst() {
        module.manage.create(owner, byDate("Primera", "100", JAN_16));
        module.clock.advanceDays(1);
        module.manage.create(owner, byDate("Segunda", "100", JAN_16.plusMonths(1)));
        module.manage.create(stranger, byDate("Ajena", "100", JAN_16.plusMonths(1)));

        assertThat(module.manage.list(owner)).extracting(view -> view.goal().name())
                .containsExactly("Segunda", "Primera");
        assertThat(module.manage.list(UserId.newId())).isEmpty();
    }

    @Test
    void anotherUsersGoalDoesNotExist() {
        SavingsGoal foreign = module.manage.create(stranger, byDate("Ajena", "100", JAN_16)).goal();

        assertThatThrownBy(() -> module.manage.get(owner, foreign.id()))
                .isInstanceOf(SavingsGoalNotFoundException.class);
        assertThatThrownBy(() -> module.manage.update(owner, foreign.id(), byDate("Mía", "1", JAN_16)))
                .isInstanceOf(SavingsGoalNotFoundException.class);
        assertThatThrownBy(() -> module.manage.delete(owner, foreign.id()))
                .isInstanceOf(SavingsGoalNotFoundException.class);

        assertThat(module.goals.stored.get(foreign.id()).name()).isEqualTo("Ajena");
    }

    @Test
    void anOverdueGoalCanBeEditedKeepingItsDate() {
        SavingsGoal goal = module.manage.create(owner, byDate("Consola", "1200000", JAN_16)).goal();
        module.clock.advanceDays(30);

        SavingsGoalView updated = module.manage.update(owner, goal.id(), byDate("Consola nueva", "1500000", JAN_16));

        assertThat(updated.goal().name()).isEqualTo("Consola nueva");
        assertThat(updated.goal().target()).isEqualTo(cop("1500000"));
        assertThat(updated.projection().overdue()).isTrue();
        assertThat(updated.goal().updatedAt()).isEqualTo(module.clock.instant());
    }

    @Test
    void anEditCannotMoveTheDateToThePast() {
        SavingsGoal goal = module.manage.create(owner, byDate("Consola", "1200000", JAN_16)).goal();
        module.clock.advanceDays(30);

        assertThatThrownBy(() -> module.manage.update(owner, goal.id(), byDate("Otra", "1", JAN_16.plusDays(5))))
                .isInstanceOf(InvalidSavingsGoalException.class);

        assertThat(module.goals.stored.get(goal.id()).name()).isEqualTo("Consola");
        assertThat(module.goals.stored.get(goal.id()).target()).isEqualTo(cop("1200000"));
    }

    @Test
    void anInvalidEditLeavesTheGoalUntouched() {
        SavingsGoal goal = module.manage.create(owner, byDate("Consola", "1200000", JAN_16)).goal();

        assertThatThrownBy(() -> module.manage.update(owner, goal.id(), byDate("", "5", JAN_16)))
                .isInstanceOf(InvalidSavingsGoalException.class);

        assertThat(module.goals.stored.get(goal.id()).name()).isEqualTo("Consola");
        assertThat(module.goals.stored.get(goal.id()).target()).isEqualTo(cop("1200000"));
    }

    @Test
    void thePlanCanSwitchToAMonthlyAmount() {
        SavingsGoal goal = module.manage.create(owner, byDate("Consola", "900000", JAN_16)).goal();
        SavingsPlan monthly = new SavingsPlan.ByMonthlyAmount(cop("300000"));

        SavingsGoalView updated =
                module.manage.update(owner, goal.id(), new SavingsGoalCommand("Consola", cop("900000"), monthly));

        assertThat(updated.goal().plan()).isEqualTo(monthly);
        assertThat(updated.projection().estimatedDate()).isEqualTo(JAN_15.plusMonths(3));
    }

    @Test
    void deletingAGoalAlsoDeletesItsContributionsAndRates() {
        SavingsGoal goal = module.manage.create(owner, byDate("Consola", "1200000", JAN_16)).goal();
        SavingsGoal other = module.manage.create(owner, byDate("Viaje", "500000", JAN_16)).goal();
        module.contributions.add(owner, goal.id(), JAN_15, cop("1000"));
        module.contributions.add(owner, other.id(), JAN_15, cop("2000"));
        module.rate.adjust(owner, goal.id(), AnnualRate.ofPercent(new BigDecimal("10")));

        module.manage.delete(owner, goal.id());

        assertThat(module.goals.stored).containsOnlyKeys(other.id());
        assertThat(module.goals.contributions).extracting(contribution -> contribution.goalId())
                .containsExactly(other.id());
        assertThat(module.goals.rateChanges).isEmpty();
    }
}
