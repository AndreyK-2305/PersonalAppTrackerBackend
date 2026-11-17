package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.domain.model.PaymentSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Cuota de la tabla de amortización. {@code payment} es todo lo que se paga ese mes: interés, capital,
 * seguros y otros y, si se pagó de más, el abono extraordinario ({@code extraPrincipal}).
 * {@code balance} es el saldo después de pagarla. {@code paidOn} solo aparece si ya se pagó.
 */
@Schema(requiredProperties = {"number", "dueDate", "payment", "interest", "principal", "extras", "extraPrincipal",
        "balance", "paid"})
record InstallmentResponse(
        int number,
        LocalDate dueDate,
        String payment,
        String interest,
        String principal,
        String extras,
        String extraPrincipal,
        String balance,
        boolean paid,
        LocalDate paidOn) {

    static InstallmentResponse from(PaymentSchedule.Installment installment) {
        return new InstallmentResponse(
                installment.number(),
                installment.dueDate(),
                GoalAmounts.format(installment.payment()),
                GoalAmounts.format(installment.interest()),
                GoalAmounts.format(installment.principal()),
                GoalAmounts.format(installment.extras()),
                GoalAmounts.format(installment.extraPrincipal()),
                GoalAmounts.format(installment.balance()),
                installment.paid(),
                installment.paidOn());
    }
}
