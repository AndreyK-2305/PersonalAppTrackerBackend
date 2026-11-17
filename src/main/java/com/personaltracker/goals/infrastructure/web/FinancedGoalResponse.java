package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Meta financiada con el resumen de su plan actual y el estado de su crédito (ADR-0018).
 * {@code ratePercent} y {@code actualInstallment} son las condiciones originales; lo vigente tras
 * abonos y ajustes de tasa está en {@code summary} y {@code progress}.
 */
@Schema(requiredProperties = {"id", "name", "assetValue", "downPayment", "currency", "ratePercent", "installments",
        "firstDueDate", "summary", "progress", "createdAt", "updatedAt"})
record FinancedGoalResponse(
        UUID id,
        String name,
        String assetValue,
        String downPayment,
        String currency,
        String ratePercent,
        int installments,
        LocalDate firstDueDate,
        String actualInstallment,
        PlanSummaryResponse summary,
        CreditProgressResponse progress,
        Instant createdAt,
        Instant updatedAt) {

    static FinancedGoalResponse from(FinancedGoalView view) {
        FinancedGoal goal = view.goal();
        FinancedTerms terms = goal.terms();
        return new FinancedGoalResponse(
                goal.id().value(),
                goal.name(),
                GoalAmounts.format(terms.assetValue()),
                GoalAmounts.format(terms.downPayment()),
                terms.assetValue().currency().getCurrencyCode(),
                GoalAmounts.format(terms.rate()),
                terms.installments(),
                terms.firstDueDate(),
                GoalAmounts.format(terms.actualInstallment()),
                PlanSummaryResponse.from(view.schedule()),
                CreditProgressResponse.from(view),
                goal.createdAt(),
                goal.updatedAt());
    }
}
