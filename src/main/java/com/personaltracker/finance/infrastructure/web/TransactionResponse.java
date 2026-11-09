package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.TransactionView;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.TransactionOrigin;
import com.personaltracker.finance.domain.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento. {@code amount} es una cadena decimal en {@code currency}. Según el tipo, son nulos
 * {@code sourceAccountId} (ingreso), {@code destinationAccountId} (gasto), {@code categoryId}
 * (transferencia, ajuste o sin categoría) y {@code direction} (todo salvo ajustes). Un ajuste indica
 * su producto en {@code destinationAccountId} si aumenta el saldo y en {@code sourceAccountId} si lo
 * disminuye. {@code origin} es MANUAL o CAPTURE; {@code amountEstimated} indica un monto convertido
 * de dólares con la TRM, que el usuario puede corregir con el valor del extracto.
 */
@Schema(requiredProperties = {
    "id", "type", "amount", "currency", "date", "origin", "amountEstimated", "createdAt", "updatedAt"
})
record TransactionResponse(
        UUID id,
        TransactionType type,
        String amount,
        String currency,
        LocalDate date,
        String description,
        String merchant,
        PaymentChannel channel,
        UUID sourceAccountId,
        UUID destinationAccountId,
        UUID categoryId,
        AdjustmentDirection direction,
        TransactionOrigin origin,
        boolean amountEstimated,
        Instant createdAt,
        Instant updatedAt) {

    static TransactionResponse from(TransactionView view) {
        return new TransactionResponse(
                view.id().value(),
                view.type(),
                Amounts.format(view.amount()),
                view.amount().currency().getCurrencyCode(),
                view.date(),
                view.description(),
                view.merchant(),
                view.channel(),
                view.sourceAccount() == null ? null : view.sourceAccount().value(),
                view.destinationAccount() == null ? null : view.destinationAccount().value(),
                view.category() == null ? null : view.category().value(),
                view.direction(),
                view.origin(),
                view.amountEstimated(),
                view.createdAt(),
                view.updatedAt());
    }
}
