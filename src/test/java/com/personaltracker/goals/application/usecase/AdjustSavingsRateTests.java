package com.personaltracker.goals.application.usecase;

import static com.personaltracker.goals.application.usecase.GoalsFakes.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.dto.SavingsGoalCommand;
import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.domain.exception.SavingsGoalNotFoundException;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Rentabilidad de una meta de ahorro con dobles de los puertos. En Bogotá es 15 de enero; en Tokio, 16. */
class AdjustSavingsRateTests {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    private final GoalsFakes.Module module = new GoalsFakes.Module(Instant.parse("2026-01-16T03:00:00Z"));
    private final UserId owner = UserId.newId();

    private static AnnualRate percent(String value) {
        return AnnualRate.ofPercent(new BigDecimal(value));
    }

    private SavingsGoalId createGoal(UserId user) {
        return module.manage.create(user, new SavingsGoalCommand("Fondo", cop("1200000"),
                new SavingsPlan.ByTargetDate(JAN_15.plusMonths(12).plusDays(1)))).goal().id();
    }

    @Test
    void theRateStartsTheDayAfterTodayInTheOwnersTimeZone() {
        UserId inTokyo = UserId.newId();
        module.owners.zones.put(inTokyo, GoalsFakes.TOKYO);

        SavingsGoalView bogota = module.rate.adjust(owner, createGoal(owner), percent("12"));
        SavingsGoalView tokyo = module.rate.adjust(inTokyo, createGoal(inTokyo), percent("12"));

        assertThat(bogota.rateChanges()).extracting(SavingsRateChange::effectiveFrom)
                .containsExactly(JAN_15.plusDays(1));
        assertThat(tokyo.rateChanges()).extracting(SavingsRateChange::effectiveFrom)
                .containsExactly(JAN_15.plusDays(2));
        assertThat(bogota.projection().rate()).isEqualTo(percent("12"));
    }

    @Test
    void aSecondChangeOnTheSameDayReplacesThePendingOne() {
        SavingsGoalId goalId = createGoal(owner);

        module.rate.adjust(owner, goalId, percent("8"));
        SavingsGoalView view = module.rate.adjust(owner, goalId, percent("12"));

        assertThat(view.rateChanges()).hasSize(1);
        assertThat(view.rateChanges().get(0).rate()).isEqualTo(percent("12"));
    }

    @Test
    void aChangeOnALaterDayKeepsTheHistory() {
        SavingsGoalId goalId = createGoal(owner);

        module.rate.adjust(owner, goalId, percent("8"));
        module.clock.advanceDays(1);
        SavingsGoalView view = module.rate.adjust(owner, goalId, percent("12"));

        assertThat(view.rateChanges()).extracting(SavingsRateChange::effectiveFrom)
                .containsExactly(JAN_15.plusDays(1), JAN_15.plusDays(2));
        assertThat(view.projection().rate()).isEqualTo(percent("12"));
    }

    @Test
    void aZeroRateTurnsTheYieldOffAndKeepsTheHistory() {
        SavingsGoalId goalId = createGoal(owner);

        module.rate.adjust(owner, goalId, percent("12"));
        module.clock.advanceDays(3);
        SavingsGoalView view = module.rate.adjust(owner, goalId, AnnualRate.ZERO);

        assertThat(view.projection().rate().isZero()).isTrue();
        assertThat(view.rateChanges()).hasSize(2);
    }

    @Test
    void movingTheClockBackNeverLeavesTwoChangesForTheSameDay() {
        SavingsGoalId goalId = createGoal(owner);
        module.rate.adjust(owner, goalId, percent("8"));

        // El usuario cambia a una zona horaria donde todavía es el día anterior.
        module.clock.set(module.clock.instant().minusSeconds(86_400));
        SavingsGoalView view = module.rate.adjust(owner, goalId, percent("9"));

        assertThat(view.rateChanges()).extracting(SavingsRateChange::effectiveFrom).containsExactly(JAN_15);
    }

    @Test
    void nobodyCanChangeTheRateOfAnotherUsersGoal() {
        SavingsGoalId goalId = createGoal(owner);

        assertThatThrownBy(() -> module.rate.adjust(UserId.newId(), goalId, percent("12")))
                .isInstanceOf(SavingsGoalNotFoundException.class);

        assertThat(module.goals.rateChanges).isEmpty();
    }
}
