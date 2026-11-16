package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.domain.model.PaymentSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Plan de pagos calculado sin guardar: el resumen y la tabla de amortización. */
@Schema(requiredProperties = {"currency", "summary", "schedule"})
record SimulationResponse(String currency, PlanSummaryResponse summary, List<InstallmentResponse> schedule) {

    static SimulationResponse from(PaymentSchedule schedule) {
        return new SimulationResponse(
                schedule.financedAmount().currency().getCurrencyCode(),
                PlanSummaryResponse.from(schedule),
                schedule.installments().stream().map(InstallmentResponse::from).toList());
    }
}
