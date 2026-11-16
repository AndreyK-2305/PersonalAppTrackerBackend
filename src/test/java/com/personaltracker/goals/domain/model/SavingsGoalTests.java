package com.personaltracker.goals.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.domain.exception.InvalidContributionException;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Invariantes del agregado y de sus objetos de valor (ADR-0018). */
class SavingsGoalTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final UserId OWNER = UserId.newId();
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final SavingsPlan BY_DATE = new SavingsPlan.ByTargetDate(LocalDate.of(2026, 12, 1));

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static SavingsGoal goal() {
        return SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Consola", cop("1200000"), BY_DATE, NOW);
    }

    @Test
    void trimsTheName() {
        SavingsGoal goal = SavingsGoal.create(SavingsGoalId.newId(), OWNER, "  Consola  ", cop("1"), BY_DATE, NOW);

        assertThat(goal.name()).isEqualTo("Consola");
        assertThat(goal.createdAt()).isEqualTo(NOW);
        assertThat(goal.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankName(String name) {
        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, name, cop("1"), BY_DATE, NOW))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void acceptsANameOfExactlyTheMaximumLengthAndRejectsOneMore() {
        String max = "a".repeat(SavingsGoal.MAX_NAME_LENGTH);

        assertThat(SavingsGoal.create(SavingsGoalId.newId(), OWNER, max, cop("1"), BY_DATE, NOW).name()).isEqualTo(max);
        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, max + "a", cop("1"), BY_DATE, NOW))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void rejectsATargetThatIsNotPositive(String target) {
        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Consola", cop(target), BY_DATE, NOW))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void rejectsAMonthlyAmountThatIsNotPositive() {
        assertThatThrownBy(() -> new SavingsPlan.ByMonthlyAmount(cop("0")))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void rejectsAMonthlyAmountInAnotherCurrency() {
        SavingsPlan usdPlan =
                new SavingsPlan.ByMonthlyAmount(new Money(new BigDecimal("100"), Currency.getInstance("USD")));

        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Consola", cop("1"), usdPlan, NOW))
                .isInstanceOf(InvalidSavingsGoalException.class);
    }

    @Test
    void updateReplacesTheEditableDataAndKeepsTheCreationInstant() {
        SavingsGoal goal = goal();
        Instant later = NOW.plusSeconds(60);
        SavingsPlan monthly = new SavingsPlan.ByMonthlyAmount(cop("150000"));

        goal.update(" Viaje ", cop("3000000"), monthly, later);

        assertThat(goal.name()).isEqualTo("Viaje");
        assertThat(goal.target()).isEqualTo(cop("3000000"));
        assertThat(goal.plan()).isEqualTo(monthly);
        assertThat(goal.createdAt()).isEqualTo(NOW);
        assertThat(goal.updatedAt()).isEqualTo(later);
    }

    @Test
    void anInvalidUpdateLeavesTheGoalUntouched() {
        SavingsGoal goal = goal();

        assertThatThrownBy(() -> goal.update("Viaje", cop("0"), BY_DATE, NOW.plusSeconds(60)))
                .isInstanceOf(InvalidSavingsGoalException.class);

        assertThat(goal.name()).isEqualTo("Consola");
        assertThat(goal.target()).isEqualTo(cop("1200000"));
        assertThat(goal.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-500"})
    void rejectsAContributionThatIsNotPositive(String amount) {
        assertThatThrownBy(() -> new SavingsContribution(UUID.randomUUID(), SavingsGoalId.newId(), OWNER,
                LocalDate.of(2026, 1, 15), cop(amount), NOW)).isInstanceOf(InvalidContributionException.class);
    }

    @Test
    void everyRuleHasItsOwnStableCode() {
        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, "", cop("1"), BY_DATE, NOW))
                .isInstanceOfSatisfying(InvalidSavingsGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.savings_goal.name_invalid"));
        assertThatThrownBy(() -> SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Meta", cop("0"), BY_DATE, NOW))
                .isInstanceOfSatisfying(InvalidSavingsGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.savings_goal.target_invalid"));
        assertThatThrownBy(() -> new SavingsPlan.ByMonthlyAmount(cop("0")))
                .isInstanceOfSatisfying(InvalidSavingsGoalException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.savings_goal.monthly_amount_invalid"));
        assertThatThrownBy(() -> new SavingsContribution(UUID.randomUUID(), SavingsGoalId.newId(), OWNER,
                LocalDate.of(2026, 1, 15), cop("0"), NOW))
                .isInstanceOfSatisfying(InvalidContributionException.class,
                        ex -> assertThat(ex.code()).isEqualTo("goals.contribution.amount_invalid"));
    }

    @Test
    void eachPlanKnowsItsKind() {
        assertThat(BY_DATE.kind()).isEqualTo(SavingsPlanKind.TARGET_DATE);
        assertThat(new SavingsPlan.ByMonthlyAmount(cop("1")).kind()).isEqualTo(SavingsPlanKind.MONTHLY_AMOUNT);
    }
}
