package com.personaltracker.goals.application.usecase;

import static com.personaltracker.goals.application.usecase.GoalsFakes.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.dto.SavingsGoalCommand;
import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.domain.exception.ContributionNotFoundException;
import com.personaltracker.goals.domain.exception.InvalidContributionException;
import com.personaltracker.goals.domain.exception.SavingsGoalNotFoundException;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Abonos de una meta de ahorro con dobles de los puertos. En Bogotá es 15 de enero; en Tokio, 16. */
class RecordSavingsContributionsTests {

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);
    private static final LocalDate JAN_16 = LocalDate.of(2026, 1, 16);

    private final GoalsFakes.Module module = new GoalsFakes.Module(Instant.parse("2026-01-16T03:00:00Z"));
    private final UserId owner = UserId.newId();
    private SavingsGoalId goalId;

    @BeforeEach
    void createGoal() {
        goalId = createGoal(owner, "Consola");
    }

    private SavingsGoalId createGoal(UserId user, String name) {
        return module.manage.create(user, new SavingsGoalCommand(name, cop("1000000"),
                new SavingsPlan.ByTargetDate(LocalDate.of(2026, 11, 15)))).goal().id();
    }

    @Test
    void contributionsAddUpNewestFirst() {
        module.contributions.add(owner, goalId, JAN_15.minusDays(10), cop("100000"));
        SavingsGoalView view = module.contributions.add(owner, goalId, JAN_15, cop("250000.50"));

        assertThat(view.projection().saved()).isEqualTo(cop("350000.50"));
        assertThat(view.contributions()).extracting(SavingsContribution::date)
                .containsExactly(JAN_15, JAN_15.minusDays(10));
        assertThat(view.contributions()).allSatisfy(contribution -> {
            assertThat(contribution.owner()).isEqualTo(owner);
            assertThat(contribution.createdAt()).isEqualTo(module.clock.instant());
        });
    }

    @Test
    void theDateCannotBeAfterTodayInTheOwnersTimeZone() {
        UserId inTokyo = UserId.newId();
        module.owners.zones.put(inTokyo, GoalsFakes.TOKYO);
        SavingsGoalId tokyoGoal = createGoal(inTokyo, "Tokio");

        assertThatThrownBy(() -> module.contributions.add(owner, goalId, JAN_16, cop("1000")))
                .isInstanceOfSatisfying(InvalidContributionException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.contribution.date_invalid"));
        assertThat(module.contributions.add(inTokyo, tokyoGoal, JAN_16, cop("1000")).projection().saved())
                .isEqualTo(cop("1000"));
        assertThat(module.goals.contributions).hasSize(1);
    }

    @Test
    void theAmountMustBePositiveAndInTheGoalsCurrency() {
        Money usd = new Money(new BigDecimal("10"), Currency.getInstance("USD"));

        assertThatThrownBy(() -> module.contributions.add(owner, goalId, JAN_15, cop("0")))
                .isInstanceOf(InvalidContributionException.class);
        assertThatThrownBy(() -> module.contributions.add(owner, goalId, JAN_15, usd))
                .isInstanceOfSatisfying(InvalidContributionException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.contribution.amount_invalid"));

        assertThat(module.goals.contributions).isEmpty();
    }

    @Test
    void theDateCannotBeMoreThanAHundredYearsOld() {
        SavingsGoalView atTheLimit = module.contributions.add(owner, goalId, JAN_15.minusYears(100), cop("1000"));

        assertThat(atTheLimit.contributions()).hasSize(1);
        assertThatThrownBy(() -> module.contributions.add(owner, goalId, JAN_15.minusYears(100).minusDays(1),
                cop("1000"))).isInstanceOfSatisfying(InvalidContributionException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.contribution.date_invalid"));
        assertThatThrownBy(() -> module.contributions.add(owner, goalId, LocalDate.MIN, cop("1000")))
                .isInstanceOf(InvalidContributionException.class);
        assertThat(module.goals.contributions).hasSize(1);
    }

    @Test
    void nobodyCanContributeToAnotherUsersGoal() {
        assertThatThrownBy(() -> module.contributions.add(UserId.newId(), goalId, JAN_15, cop("1000")))
                .isInstanceOf(SavingsGoalNotFoundException.class);

        assertThat(module.goals.contributions).isEmpty();
    }

    @Test
    void removingAContributionOnlyRemovesThatOne() {
        module.contributions.add(owner, goalId, JAN_15, cop("100000"));
        SavingsGoalView view = module.contributions.add(owner, goalId, JAN_15, cop("50000"));
        UUID first = view.contributions().stream()
                .filter(contribution -> contribution.amount().equals(cop("100000")))
                .findFirst().orElseThrow().id();

        SavingsGoalView afterRemoval = module.contributions.remove(owner, goalId, first);

        assertThat(afterRemoval.projection().saved()).isEqualTo(cop("50000"));
        assertThat(afterRemoval.contributions()).hasSize(1);
    }

    @Test
    void aContributionOfAnotherGoalCannotBeRemovedThroughThisOne() {
        SavingsGoalId otherGoal = createGoal(owner, "Viaje");
        UUID foreign = module.contributions.add(owner, otherGoal, JAN_15, cop("1000")).contributions().get(0).id();

        assertThatThrownBy(() -> module.contributions.remove(owner, goalId, foreign))
                .isInstanceOf(ContributionNotFoundException.class);
        assertThatThrownBy(() -> module.contributions.remove(owner, goalId, UUID.randomUUID()))
                .isInstanceOf(ContributionNotFoundException.class);
        assertThatThrownBy(() -> module.contributions.remove(UserId.newId(), otherGoal, foreign))
                .isInstanceOf(SavingsGoalNotFoundException.class);

        assertThat(module.goals.contributions).hasSize(1);
    }

    @Test
    void reachingTheTargetCompletesTheGoal() {
        module.contributions.add(owner, goalId, JAN_15, cop("600000"));
        SavingsGoalView view = module.contributions.add(owner, goalId, JAN_15, cop("400000"));

        assertThat(view.projection().completed()).isTrue();
        assertThat(view.projection().recommendedMonthly()).isEqualTo(cop("0"));
    }
}
