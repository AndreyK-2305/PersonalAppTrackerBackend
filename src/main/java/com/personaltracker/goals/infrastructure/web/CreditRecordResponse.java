package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.domain.model.CreditPlan.AppliedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.goals.domain.model.PrepaymentMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de un crédito. Según {@code kind}:
 *
 * <ul>
 *   <li>{@code PAYMENT}: {@code amount}, {@code installmentNumber} y {@code mode} (cómo se aplica lo
 *       pagado de más).</li>
 *   <li>{@code PREPAYMENT}: {@code amount} y {@code mode}.</li>
 *   <li>{@code RATE_ADJUSTMENT}: {@code ratePercent} y, si se registró, {@code actualInstallment}.</li>
 * </ul>
 *
 * {@code undoable} solo es verdadero en el último registro.
 */
@Schema(requiredProperties = {"id", "kind", "date", "undoable"})
record CreditRecordResponse(
        UUID id,
        FinancedRecord.Kind kind,
        LocalDate date,
        String amount,
        PrepaymentMode mode,
        Integer installmentNumber,
        String ratePercent,
        String actualInstallment,
        boolean undoable) {

    static CreditRecordResponse from(AppliedRecord applied, boolean undoable) {
        FinancedRecord record = applied.record();
        return switch (record) {
            case InstallmentPayment payment -> new CreditRecordResponse(payment.id(), payment.kind(), payment.date(),
                    GoalAmounts.format(payment.amount()), payment.excessMode(), applied.installmentNumber(), null,
                    null, undoable);
            case Prepayment prepayment -> new CreditRecordResponse(prepayment.id(), prepayment.kind(),
                    prepayment.date(), GoalAmounts.format(prepayment.amount()), prepayment.mode(), null, null, null,
                    undoable);
            case RateAdjustment adjustment -> new CreditRecordResponse(adjustment.id(), adjustment.kind(),
                    adjustment.date(), null, null, null, GoalAmounts.format(adjustment.rate()),
                    GoalAmounts.format(adjustment.actualInstallment()), undoable);
        };
    }
}
