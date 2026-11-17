package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.model.CreditPlan.AppliedRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Una meta financiada con su tabla de amortización completa (cuotas pagadas y pendientes) y sus
 * registros, del más reciente al más antiguo.
 */
@Schema(requiredProperties = {"goal", "schedule", "records"})
record FinancedGoalDetailResponse(
        FinancedGoalResponse goal, List<InstallmentResponse> schedule, List<CreditRecordResponse> records) {

    static FinancedGoalDetailResponse from(FinancedGoalView view) {
        List<AppliedRecord> applied = view.plan().records();
        List<CreditRecordResponse> records = new ArrayList<>();
        for (int index = applied.size() - 1; index >= 0; index--) {
            records.add(CreditRecordResponse.from(applied.get(index), index == applied.size() - 1));
        }
        return new FinancedGoalDetailResponse(
                FinancedGoalResponse.from(view),
                view.schedule().installments().stream().map(InstallmentResponse::from).toList(),
                records);
    }
}
