package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsPlanKind;
import com.personaltracker.goals.domain.model.SavingsProjection;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Meta de ahorro con su proyección de hoy (ADR-0018). Montos como cadenas decimales y tasas como
 * porcentajes EA. Según el plan:
 *
 * <ul>
 *   <li>{@code TARGET_DATE}: {@code monthsRemaining} y {@code recommendedMonthlyAmount};</li>
 *   <li>{@code MONTHLY_AMOUNT}: {@code estimatedDate} y {@code reachable} (falso si pasa de 100 años).</li>
 * </ul>
 */
@Schema(requiredProperties = {"id", "name", "targetAmount", "currency", "plan", "savedAmount", "remainingAmount",
        "progressPercent", "completed", "overdue", "reachable", "contributions", "rateChanges", "createdAt",
        "updatedAt"})
record SavingsGoalResponse(
        UUID id,
        String name,
        String targetAmount,
        String currency,
        SavingsPlanKind plan,
        LocalDate targetDate,
        String monthlyAmount,
        String savedAmount,
        String remainingAmount,
        String progressPercent,
        boolean completed,
        boolean overdue,
        Integer monthsRemaining,
        String recommendedMonthlyAmount,
        LocalDate estimatedDate,
        boolean reachable,
        @Schema(description = "Tasa EA de la proyección en porcentaje; ausente si la rentabilidad está apagada")
                String ratePercent,
        List<ContributionResponse> contributions,
        List<RateChangeResponse> rateChanges,
        Instant createdAt,
        Instant updatedAt) {

    static SavingsGoalResponse from(SavingsGoalView view) {
        SavingsGoal goal = view.goal();
        SavingsProjection projection = view.projection();
        LocalDate targetDate = null;
        Money monthlyAmount = null;
        switch (goal.plan()) {
            case SavingsPlan.ByTargetDate(LocalDate date) -> targetDate = date;
            case SavingsPlan.ByMonthlyAmount(Money monthly) -> monthlyAmount = monthly;
        }
        boolean byDate = goal.plan().kind() == SavingsPlanKind.TARGET_DATE;
        return new SavingsGoalResponse(
                goal.id().value(),
                goal.name(),
                amount(goal.target()),
                goal.target().currency().getCurrencyCode(),
                goal.plan().kind(),
                targetDate,
                amount(monthlyAmount),
                amount(projection.saved()),
                amount(projection.remaining()),
                projection.progressPercent().toPlainString(),
                projection.completed(),
                projection.overdue(),
                projection.monthsRemaining(),
                byDate ? amount(projection.recommendedMonthly()) : null,
                projection.estimatedDate(),
                projection.reachable(),
                projection.rate().isZero() ? null : projection.rate().percent().stripTrailingZeros().toPlainString(),
                view.contributions().stream().map(ContributionResponse::from).toList(),
                view.rateChanges().stream().map(RateChangeResponse::from).toList(),
                goal.createdAt(),
                goal.updatedAt());
    }

    private static String amount(Money money) {
        return money == null ? null : money.amount().toPlainString();
    }

    @Schema(requiredProperties = {"id", "date", "amount", "createdAt"})
    record ContributionResponse(UUID id, LocalDate date, String amount, Instant createdAt) {

        static ContributionResponse from(SavingsContribution contribution) {
            return new ContributionResponse(contribution.id(), contribution.date(),
                    contribution.amount().amount().toPlainString(), contribution.createdAt());
        }
    }

    /** {@code ratePercent} "0" significa que desde esa fecha la rentabilidad está apagada. */
    @Schema(requiredProperties = {"effectiveFrom", "ratePercent"})
    record RateChangeResponse(LocalDate effectiveFrom, String ratePercent) {

        static RateChangeResponse from(SavingsRateChange change) {
            return new RateChangeResponse(change.effectiveFrom(),
                    change.rate().percent().stripTrailingZeros().toPlainString());
        }
    }
}
