package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Una meta financiada con su tabla de amortización completa. */
@Schema(requiredProperties = {"goal", "schedule"})
record FinancedGoalDetailResponse(FinancedGoalResponse goal, List<InstallmentResponse> schedule) {

    static FinancedGoalDetailResponse from(FinancedGoalView view) {
        return new FinancedGoalDetailResponse(
                FinancedGoalResponse.from(view),
                view.schedule().installments().stream().map(InstallmentResponse::from).toList());
    }
}
