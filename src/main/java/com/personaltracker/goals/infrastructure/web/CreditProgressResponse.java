package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.model.CreditPlan;
import com.personaltracker.goals.domain.model.CreditStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cómo va el crédito (ADR-0018).
 *
 * <ul>
 *   <li>{@code status}: {@code ON_TRACK}, {@code OVERDUE} (la próxima cuota ya venció) o
 *       {@code PAID_OFF}. Es informativo: no hay mora.</li>
 *   <li>{@code interestSavings}: intereses del plan original menos los del plan actual. Negativo si
 *       ahora se pagan más, por ejemplo tras una subida de tasa.</li>
 *   <li>{@code nextInstallment}: ausente si el crédito está pagado.</li>
 *   <li>{@code hasRecords}: con registros, de la meta solo se puede cambiar el nombre.</li>
 * </ul>
 */
@Schema(requiredProperties = {"status", "paidInstallments", "pendingInstallments", "balance", "currentRatePercent",
        "paidInterest", "pendingInterest", "originalTotalInterest", "interestSavings", "paidSoFar", "hasRecords"})
record CreditProgressResponse(
        CreditStatus status,
        int paidInstallments,
        int pendingInstallments,
        String balance,
        String currentRatePercent,
        String paidInterest,
        String pendingInterest,
        String originalTotalInterest,
        String interestSavings,
        String paidSoFar,
        InstallmentResponse nextInstallment,
        boolean hasRecords) {

    static CreditProgressResponse from(FinancedGoalView view) {
        CreditPlan plan = view.plan();
        return new CreditProgressResponse(
                view.status(),
                plan.paidInstallments(),
                plan.pendingInstallments(),
                GoalAmounts.format(plan.balance()),
                GoalAmounts.format(plan.currentRate()),
                GoalAmounts.format(plan.paidInterest()),
                GoalAmounts.format(plan.pendingInterest()),
                GoalAmounts.format(plan.originalTotalInterest()),
                GoalAmounts.format(plan.interestSavings()),
                GoalAmounts.format(plan.paidSoFar()),
                plan.nextInstallment() == null ? null : InstallmentResponse.from(plan.nextInstallment()),
                plan.hasRecords());
    }
}
