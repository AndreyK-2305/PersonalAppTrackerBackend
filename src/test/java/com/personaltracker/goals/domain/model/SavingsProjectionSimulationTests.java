package com.personaltracker.goals.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import com.personaltracker.shared.domain.finance.FinancialMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Simula el ahorro mes a mes para comprobar la proyección contra un cálculo independiente de la
 * fórmula cerrada (ADR-0018): el aporte recomendado alcanza el objetivo, un peso menos no alcanza, y
 * los dos tipos de plan son coherentes entre sí.
 */
class SavingsProjectionSimulationTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final UserId OWNER = UserId.newId();
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static Money cop(BigDecimal amount) {
        return new Money(amount, COP);
    }

    private static SavingsGoal byDate(String target, LocalDate date) {
        return SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Meta", cop(target),
                new SavingsPlan.ByTargetDate(date), NOW);
    }

    private static List<SavingsContribution> saved(SavingsGoal goal, String amount) {
        return new BigDecimal(amount).signum() == 0 ? List.of()
                : List.of(new SavingsContribution(UUID.randomUUID(), goal.id(), OWNER, TODAY, cop(amount), NOW));
    }

    private static List<SavingsRateChange> rate(SavingsGoal goal, String percent) {
        return new BigDecimal(percent).signum() == 0 ? List.of()
                : List.of(new SavingsRateChange(UUID.randomUUID(), goal.id(), OWNER, TODAY,
                        AnnualRate.ofPercent(new BigDecimal(percent)), NOW));
    }

    /** Saldo al cabo de {@code months} meses: cada mes rinde la tasa mensual y luego entra el aporte. */
    private static BigDecimal simulate(String saved, BigDecimal contribution, String ratePercent, int months) {
        BigDecimal factor = BigDecimal.ONE.add(AnnualRate.ofPercent(new BigDecimal(ratePercent)).monthly());
        BigDecimal balance = new BigDecimal(saved);
        for (int month = 0; month < months; month++) {
            balance = balance.multiply(factor, FinancialMath.CONTEXT).add(contribution, FinancialMath.CONTEXT);
        }
        return balance;
    }

    @ParameterizedTest(name = "objetivo {0}, ahorrado {1}, {2} % EA, {3} meses")
    @CsvSource({
        "1200000,    0,        12,     12",
        "10000000,   1000000,  10,     24",
        "50000000,   3500000,  9.25,   36",
        "2500000,    100000,   0,      7",
        "300000000,  25000000, 11.5,   120",
        "999999,     1,        0.0001, 1",
        "1000000,    0,        1000,   24",
        "85000000,   0,        4.2,    240",
        "1000000,    999999,   5,      3",
        "7,          0,        0,      3",
        "99999999999999999.99, 0, 13.7, 1200",
    })
    void theRecommendedContributionReachesTheTargetAndOnePesoLessDoesNot(
            String target, String savedAmount, String ratePercent, int months) {
        SavingsGoal goal = byDate(target, TODAY.plusMonths(months));

        SavingsProjection projection =
                SavingsProjection.of(goal, saved(goal, savedAmount), rate(goal, ratePercent), TODAY);

        BigDecimal recommended = projection.recommendedMonthly().amount();
        assertThat(projection.monthsRemaining()).isEqualTo(months);
        assertThat(simulate(savedAmount, recommended, ratePercent, months))
                .as("con el aporte recomendado se llega al objetivo")
                .isGreaterThanOrEqualTo(new BigDecimal(target));
        if (recommended.signum() > 0) {
            assertThat(simulate(savedAmount, recommended.subtract(BigDecimal.ONE), ratePercent, months))
                    .as("con un peso menos al mes no se llega")
                    .isLessThan(new BigDecimal(target));
        }
    }

    @ParameterizedTest(name = "objetivo {0}, ahorrado {1}, {2} % EA, {3} meses")
    @CsvSource({
        "1200000,   0,        12,   12",
        "10000000,  1000000,  10,   24",
        "50000000,  3500000,  9.25, 36",
        "2500000,   100000,   0,    7",
        "300000000, 25000000, 11.5, 120",
    })
    void bothPlansAgree(String target, String savedAmount, String ratePercent, int months) {
        SavingsGoal byDate = byDate(target, TODAY.plusMonths(months));
        BigDecimal recommended = SavingsProjection
                .of(byDate, saved(byDate, savedAmount), rate(byDate, ratePercent), TODAY)
                .recommendedMonthly().amount();

        SavingsProjection sameContribution = monthlyPlan(target, savedAmount, ratePercent, recommended);
        SavingsProjection onePesoLess =
                monthlyPlan(target, savedAmount, ratePercent, recommended.subtract(BigDecimal.ONE));

        assertThat(sameContribution.monthsRemaining()).isEqualTo(months);
        assertThat(sameContribution.estimatedDate()).isEqualTo(TODAY.plusMonths(months));
        assertThat(onePesoLess.monthsRemaining()).isGreaterThan(months);
    }

    private static SavingsProjection monthlyPlan(
            String target, String savedAmount, String ratePercent, BigDecimal monthly) {
        SavingsGoal goal = SavingsGoal.create(SavingsGoalId.newId(), OWNER, "Meta", cop(target),
                new SavingsPlan.ByMonthlyAmount(cop(monthly)), NOW);
        return SavingsProjection.of(goal, saved(goal, savedAmount), rate(goal, ratePercent), TODAY);
    }

    /** Valores calculados aparte con 60 dígitos: 333.774,73… y 1.106.100,82… */
    @Test
    void matchesIndependentlyComputedValues() {
        SavingsGoal first = byDate("10000000", TODAY.plusMonths(24));
        SavingsGoal second = byDate("50000000", TODAY.plusMonths(36));

        assertThat(SavingsProjection.of(first, saved(first, "1000000"), rate(first, "10"), TODAY)
                .recommendedMonthly()).isEqualTo(cop("333775"));
        assertThat(SavingsProjection.of(second, saved(second, "3500000"), rate(second, "9.25"), TODAY)
                .recommendedMonthly()).isEqualTo(cop("1106101"));
    }

    @Test
    void lessThanAFullMonthLeftAsksForEverythingAtOnceWithoutBeingOverdue() {
        SavingsGoal goal = byDate("500000", LocalDate.of(2026, 2, 28));

        SavingsProjection projection =
                SavingsProjection.of(goal, saved(goal, "100000"), List.of(), LocalDate.of(2026, 1, 31));

        assertThat(projection.monthsRemaining()).isZero();
        assertThat(projection.overdue()).isFalse();
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("400000"));
    }

    @Test
    void progressIsRoundedDownSoAnUnfinishedGoalNeverShowsAHundredPercent() {
        SavingsGoal goal = byDate("1000000", TODAY.plusMonths(6));

        SavingsProjection projection = SavingsProjection.of(goal, saved(goal, "999999.99"), List.of(), TODAY);

        assertThat(projection.progressPercent()).isEqualByComparingTo("99.9");
        assertThat(projection.completed()).isFalse();
        assertThat(projection.remaining()).isEqualTo(cop("0.01"));
        // El aporte recomendado nunca pasa de lo que falta (H-04).
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("0.01"));
    }

    @Test
    void theDatesOfTheContributionsDoNotChangeWhatIsSaved() {
        SavingsGoal goal = byDate("1000000", TODAY.plusMonths(10));
        List<SavingsContribution> contributions = List.of(
                new SavingsContribution(UUID.randomUUID(), goal.id(), OWNER, TODAY.minusYears(3), cop("100000"), NOW),
                new SavingsContribution(UUID.randomUUID(), goal.id(), OWNER, TODAY, cop("0.50"), NOW));

        assertThat(SavingsProjection.of(goal, contributions, List.of(), TODAY).saved()).isEqualTo(cop("100000.50"));
    }

    /**
     * H-02: los meses y el aporte usan el mismo tope de 1.200 periodos. Los casos de uso ya no dejan
     * crear una meta a más de 100 años; esto protege la proyección de una fila que llegue por otra vía.
     */
    @Test
    void aTargetDateBeyondAHundredYearsKeepsMonthsAndContributionConsistent() {
        SavingsGoal goal = byDate("2400000", TODAY.plusYears(200));

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), List.of(), TODAY);

        BigDecimal months = BigDecimal.valueOf(projection.monthsRemaining());
        assertThat(projection.monthsRemaining()).isEqualTo(SavingsProjection.MAX_MONTHS);
        assertThat(projection.recommendedMonthly().amount())
                .isEqualByComparingTo(FinancialMath.ceilToUnit(new BigDecimal("2400000").divide(months,
                        FinancialMath.CONTEXT)));
    }

    /** H-04: una meta vencida por el monto máximo pide exactamente lo que falta, que sí cabe en un monto. */
    @Test
    void anOverdueGoalForTheLargestAmountStillProjects() {
        SavingsGoal goal = byDate("99999999999999999.99", TODAY.minusDays(1));

        SavingsProjection projection = SavingsProjection.of(goal, List.of(), List.of(), TODAY);

        assertThat(projection.overdue()).isTrue();
        assertThat(projection.remaining()).isEqualTo(cop("99999999999999999.99"));
        assertThat(projection.recommendedMonthly()).isEqualTo(cop("99999999999999999.99"));
    }
}
