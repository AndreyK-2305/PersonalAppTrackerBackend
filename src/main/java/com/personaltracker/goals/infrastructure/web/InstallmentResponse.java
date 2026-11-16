package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.domain.model.PaymentSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Cuota de la tabla de amortización. {@code payment} es todo lo que se paga ese mes (interés, capital
 * y seguros y otros) y {@code balance}, el saldo después de pagarla.
 */
@Schema(requiredProperties = {"number", "dueDate", "payment", "interest", "principal", "extras", "balance"})
record InstallmentResponse(
        int number, LocalDate dueDate, String payment, String interest, String principal, String extras,
        String balance) {

    static InstallmentResponse from(PaymentSchedule.Installment installment) {
        return new InstallmentResponse(
                installment.number(),
                installment.dueDate(),
                GoalAmounts.format(installment.payment()),
                GoalAmounts.format(installment.interest()),
                GoalAmounts.format(installment.principal()),
                GoalAmounts.format(installment.extras()),
                GoalAmounts.format(installment.balance()));
    }
}
