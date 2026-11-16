package com.personaltracker.goals.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavingsProjectionTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final UserId OWNER = UserId.newId();
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static SavingsGoal byDate(String target, LocalDate date) {
        return SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Consola", cop(target),
                new SavingsPlan.ByTargetDate(date), NOW);
    }

    private static SavingsGoal byMonthly(String target, String monthly) {
        return SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Consola", cop(target),
                new SavingsPlan.ByMonthlyAmount(cop(monthly)), NOW);
    }

    private static List<SavingsContribution> saved(SavingsGoal goal, String... amounts) {
        return java.util.Arrays.stream(amounts)
                .map(amount -> new SavingsContribution(UUID.randomUUID(), goal.id(), OWNER, TODAY, cop(amount), NOW))
                .toList();
    }

    private static SavingsRateChange rate(SavingsGoal goal, LocalDate from, String percent, Instant createdAt) {
        return new SavingsRateChange(UUID.randomUUID(), goal.id(), OWNER, from,
                AnnualRate.ofPercent(new BigDecimal(percent)), createdAt);
    }

    @Test
    void withoutRateSplitsWhatIsMissingEvenlyAcrossTheRemainingMonths() {
        SavingsGoal goal = byDate("1200000", LocalDate.of(2026, 11, 15));

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "150000", "50000"), List.of(), TODAY);

        assertThat(projection.saved()).isEqualTo(cop("200000"));
        assertThat(projection.remaining()).isEqualTo(cop("1000000"));
        assertThat(projection.monthsRemaining()).isEqualTo(10);
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("100000"));
        assertThat(projection.progressPercent()).isEqualByComparingTo("16.6");
        assertThat(projection.overdue()).isFalse();
        assertThat(projection.estimatedDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void roundsTheRecommendedContributionUpToThePeso() {
        SavingsGoal goal = byDate("1000000", LocalDate.of(2026, 4, 15));

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), List.of(), TODAY);

        assertThat(projection.monthsRemaining()).isEqualTo(3);
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("333334"));
    }

    @Test
    void withARateTheContributionsEarnMonthlyCompoundInterest() {
        SavingsGoal goal = byDate("1200000", LocalDate.of(2027, 1, 15));
        List<SavingsRateChange> rates = List.of(rate(goal, TODAY.plusDays(1), "12", NOW));

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), rates, TODAY);

        // C = 1.200.000 · i ÷ ((1+i)^12 − 1) = 1.200.000 · i ÷ 0,12, con i = 1,12^(1/12) − 1.
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("94888"));
        assertThat(projection.rate().percent()).isEqualByComparingTo("12");
    }

    @Test
    void usesTheLatestConfiguredRateEvenIfItStartsTomorrow() {
        SavingsGoal goal = byDate("1200000", LocalDate.of(2027, 1, 15));
        List<SavingsRateChange> rates = List.of(
                rate(goal, LocalDate.of(2025, 6, 1), "12", NOW.minusSeconds(10_000_000)),
                rate(goal, TODAY.plusDays(1), "0", NOW));

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), rates, TODAY);

        assertThat(projection.rate().isZero()).isTrue();
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("100000"));
    }

    @Test
    void whenSavingsAloneReachTheTargetTheRecommendedContributionIsZero() {
        SavingsGoal goal = byDate("1000000", LocalDate.of(2027, 1, 15));
        List<SavingsRateChange> rates = List.of(rate(goal, TODAY, "1000", NOW));

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "200000"), rates, TODAY);

        assertThat(projection.recommendedMonthly()).isEqualTo(cop("0"));
        assertThat(projection.completed()).isFalse();
    }

    @Test
    void aPastTargetDateIsOverdueAndAsksForEverythingMissingNow() {
        SavingsGoal goal = byDate("500000", LocalDate.of(2026, 1, 10));

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "100000"), List.of(), TODAY);

        assertThat(projection.overdue()).isTrue();
        assertThat(projection.monthsRemaining()).isZero();
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("400000"));
    }

    @Test
    void reachingTheTargetCompletesTheGoal() {
        SavingsGoal goal = byDate("500000", LocalDate.of(2026, 1, 10));

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "300000", "250000"), List.of(), TODAY);

        assertThat(projection.completed()).isTrue();
        assertThat(projection.overdue()).isFalse();
        assertThat(projection.remaining()).isEqualTo(cop("0"));
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("0"));
        assertThat(projection.progressPercent()).isEqualByComparingTo("110.0");
        assertThat(projection.estimatedDate()).isNull();
    }

    @Test
    void aMonthlyPlanEstimatesTheDateWhenTheGoalIsReached() {
        SavingsGoal goal = byMonthly("1000000", "300000");

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "100000"), List.of(), TODAY);

        assertThat(projection.monthsRemaining()).isEqualTo(3);
        assertThat(projection.estimatedDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(projection.reachable()).isTrue();
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("300000"));
    }

    @Test
    void aMonthlyPlanWithARateReachesTheGoalSooner() {
        SavingsGoal goal = byMonthly("1200000", "94888");
        List<SavingsRateChange> rates = List.of(rate(goal, TODAY, "12", NOW));

        SavingsProjection withRate = SavingsProjection.of(goal, List.of(), rates, TODAY);
        SavingsProjection withoutRate = SavingsProjection.of(goal, List.of(), List.of(), TODAY);

        assertThat(withRate.monthsRemaining()).isEqualTo(12);
        assertThat(withoutRate.monthsRemaining()).isEqualTo(13);
    }

    @Test
    void aMonthlyPlanThatTakesMoreThanAHundredYearsIsUnreachable() {
        SavingsGoal goal = byMonthly("1000000000", "1");

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), List.of(), TODAY);

        assertThat(projection.reachable()).isFalse();
        assertThat(projection.estimatedDate()).isNull();
        assertThat(projection.monthsRemaining()).isNull();
    }
}
