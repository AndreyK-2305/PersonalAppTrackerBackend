package com.personaltracker.goals.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.AnnualRate;
import com.personaltracker.shared.domain.finance.FinancialMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Estado y proyección de una meta de ahorro en un día dado (ADR-0018).
 *
 * <ul>
 *   <li><b>Plan por fecha:</b> {@code monthsRemaining} y el aporte mensual recomendado, redondeado hacia
 *       arriba al peso sin pasar nunca de lo que falta. Con rentabilidad, lo ya ahorrado también crece.</li>
 *   <li><b>Plan por aporte:</b> la fecha estimada, mes a mes, hasta {@link #MAX_MONTHS}; si no alcanza,
 *       {@code reachable} es falso.</li>
 * </ul>
 *
 * La rentabilidad de la proyección es la última configurada, aunque empiece a regir mañana: la
 * proyección mira hacia adelante. La app no calcula intereses ganados.
 */
public record SavingsProjection(
        Money saved,
        Money remaining,
        BigDecimal progressPercent,
        boolean completed,
        AnnualRate rate,
        Integer monthsRemaining,
        boolean overdue,
        Money recommendedMonthly,
        LocalDate estimatedDate,
        boolean reachable) {

    /** 100 años: el plazo máximo de una meta; más allá, se considera inalcanzable con el aporte elegido. */
    public static final int MAX_MONTHS = 1200;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static SavingsProjection of(
            SavingsGoal goal, List<SavingsContribution> contributions, List<SavingsRateChange> rates, LocalDate today) {
        Objects.requireNonNull(today, "today");
        Money target = goal.target();
        Money saved = contributions.stream()
                .map(SavingsContribution::amount)
                .reduce(Money.zero(target.currency()), Money::plus);
        Money remaining = saved.amount().compareTo(target.amount()) >= 0 ? Money.zero(target.currency())
                : target.minus(saved);
        boolean completed = remaining.isZero();
        BigDecimal progress = saved.amount().multiply(HUNDRED)
                .divide(target.amount(), 1, RoundingMode.DOWN);
        AnnualRate rate = latestRate(rates);
        BigDecimal monthly = rate.monthly();

        return switch (goal.plan()) {
            case SavingsPlan.ByTargetDate(LocalDate targetDate) -> {
                long months = ChronoUnit.MONTHS.between(today, targetDate);
                int periods = (int) Math.max(1, Math.min(months, MAX_MONTHS));
                boolean overdue = !completed && !targetDate.isAfter(today);
                Money recommended = completed ? Money.zero(target.currency())
                        : new Money(recommendedContribution(remaining, saved, target, monthly, periods),
                                target.currency());
                yield new SavingsProjection(saved, remaining, progress, completed, rate,
                        (int) Math.max(0, Math.min(months, MAX_MONTHS)), overdue, recommended,
                        completed ? null : targetDate, true);
            }
            case SavingsPlan.ByMonthlyAmount(Money contribution) -> {
                Integer monthsToGoal = completed ? Integer.valueOf(0)
                        : monthsToReach(saved.amount(), target.amount(), contribution.amount(), monthly);
                LocalDate estimated = completed || monthsToGoal == null ? null : today.plusMonths(monthsToGoal);
                yield new SavingsProjection(saved, remaining, progress, completed, rate, monthsToGoal, false,
                        contribution, estimated, completed || monthsToGoal != null);
            }
        };
    }

    /** La tasa con la fecha de inicio más reciente; si no hay ninguna, la rentabilidad está apagada. */
    private static AnnualRate latestRate(List<SavingsRateChange> rates) {
        return rates.stream()
                .max(Comparator.comparing(SavingsRateChange::effectiveFrom)
                        .thenComparing(SavingsRateChange::createdAt))
                .map(SavingsRateChange::rate)
                .orElse(AnnualRate.ZERO);
    }

    /**
     * Sin rentabilidad, {@code faltante ÷ n}. Con rentabilidad {@code i}, el aporte {@code C} que cumple
     * {@code ahorrado·(1+i)^n + C·((1+i)^n − 1)/i = objetivo}. Si lo ahorrado ya alcanza solo, es 0.
     * El redondeo hacia arriba nunca supera lo que falta: así el resultado siempre cabe en un monto.
     */
    private static BigDecimal recommendedContribution(
            Money remaining, Money saved, Money target, BigDecimal monthly, int periods) {
        if (monthly.signum() == 0) {
            return FinancialMath.ceilToUnit(
                    remaining.amount().divide(BigDecimal.valueOf(periods), FinancialMath.CONTEXT))
                    .min(remaining.amount());
        }
        BigDecimal growth = FinancialMath.growth(monthly, periods);
        BigDecimal missing = target.amount().subtract(saved.amount().multiply(growth, FinancialMath.CONTEXT));
        if (missing.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal contribution = missing.multiply(monthly, FinancialMath.CONTEXT)
                .divide(growth.subtract(BigDecimal.ONE), FinancialMath.CONTEXT);
        return FinancialMath.ceilToUnit(contribution).min(remaining.amount());
    }

    /** Meses hasta alcanzar el objetivo con el aporte dado; {@code null} si pasa de {@link #MAX_MONTHS}. */
    private static Integer monthsToReach(BigDecimal saved, BigDecimal target, BigDecimal contribution,
            BigDecimal monthly) {
        if (monthly.signum() == 0) {
            BigDecimal months = target.subtract(saved).divide(contribution, 0, RoundingMode.CEILING);
            return months.compareTo(BigDecimal.valueOf(MAX_MONTHS)) > 0 ? null : months.intValueExact();
        }
        BigDecimal factor = BigDecimal.ONE.add(monthly);
        BigDecimal balance = saved;
        for (int month = 1; month <= MAX_MONTHS; month++) {
            balance = balance.multiply(factor, FinancialMath.CONTEXT).add(contribution, FinancialMath.CONTEXT);
            if (balance.compareTo(target) >= 0) {
                return month;
            }
        }
        return null;
    }
}
