package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.domain.model.PaymentSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Resumen del plan de pagos actual (ADR-0018): incluye lo ya pagado y lo que falta, con la cuota y la
 * tasa vigentes.
 *
 * <ul>
 *   <li>{@code recommendedInstallment}: cuota fija calculada, redondeada hacia arriba al peso.</li>
 *   <li>{@code installmentAmount}: lo que se paga cada mes, la cuota real si se registró.</li>
 *   <li>{@code extrasPerInstallment}: "seguros y otros", la diferencia entre las dos.</li>
 *   <li>{@code totalPaid}: suma de todas las cuotas y abonos; {@code totalCost} le agrega la cuota
 *       inicial.</li>
 *   <li>{@code scheduledInstallments}: filas de la tabla; normalmente, el número de cuotas pedido.</li>
 * </ul>
 */
@Schema(requiredProperties = {"financedAmount", "recommendedInstallment", "installmentAmount",
        "extrasPerInstallment", "totalInterest", "totalExtras", "totalPaid", "totalCost", "lastDueDate",
        "scheduledInstallments"})
record PlanSummaryResponse(
        String financedAmount,
        String recommendedInstallment,
        String installmentAmount,
        String extrasPerInstallment,
        String totalInterest,
        String totalExtras,
        String totalPaid,
        String totalCost,
        LocalDate lastDueDate,
        int scheduledInstallments) {

    static PlanSummaryResponse from(PaymentSchedule schedule) {
        return new PlanSummaryResponse(
                GoalAmounts.format(schedule.financedAmount()),
                GoalAmounts.format(schedule.recommendedInstallment()),
                GoalAmounts.format(schedule.installmentAmount()),
                GoalAmounts.format(schedule.extrasPerInstallment()),
                GoalAmounts.format(schedule.totalInterest()),
                GoalAmounts.format(schedule.totalExtras()),
                GoalAmounts.format(schedule.totalPaid()),
                GoalAmounts.format(schedule.totalCost()),
                schedule.lastDueDate(),
                schedule.installments().size());
    }
}
