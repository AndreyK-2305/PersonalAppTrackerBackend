package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.usecase.AdjustCreditRate;
import com.personaltracker.goals.application.usecase.RecordCreditPayments;
import com.personaltracker.goals.application.usecase.UndoCreditRecord;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PrepaymentMode;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.infrastructure.web.ApiFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagos, abonos extraordinarios y ajustes de tasa de una meta financiada (ADR-0018). Cada operación
 * responde con la meta completa y su plan recalculado. Son marcas dentro de la meta: no crean
 * movimientos en Finanzas.
 */
@RestController
@RequestMapping("/api/v1/goals/financed/{id}")
class CreditActivityController {

    private final RecordCreditPayments payments;
    private final AdjustCreditRate adjustRate;
    private final UndoCreditRecord undoRecord;

    CreditActivityController(RecordCreditPayments payments, AdjustCreditRate adjustRate, UndoCreditRecord undoRecord) {
        this.payments = payments;
        this.adjustRate = adjustRate;
        this.undoRecord = undoRecord;
    }

    /** Paga la siguiente cuota pendiente; lo que supere su valor es un abono extraordinario. */
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    FinancedGoalDetailResponse pay(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody PaymentRequest body) {
        return FinancedGoalDetailResponse.from(payments.pay(owner, new FinancedGoalId(id), body.date(),
                GoalAmounts.cop(body.amount()), modeOrDefault(body.excessMode())));
    }

    /** Marca como pagadas todas las cuotas pendientes hasta la indicada, por su valor programado. */
    @PostMapping("/payments/through")
    @ResponseStatus(HttpStatus.CREATED)
    FinancedGoalDetailResponse payThrough(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody PayThroughRequest body) {
        return FinancedGoalDetailResponse.from(
                payments.payThrough(owner, new FinancedGoalId(id), body.installmentNumber()));
    }

    /** Abono extraordinario a capital. */
    @PostMapping("/prepayments")
    @ResponseStatus(HttpStatus.CREATED)
    FinancedGoalDetailResponse prepay(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody PrepaymentRequest body) {
        return FinancedGoalDetailResponse.from(payments.prepay(owner, new FinancedGoalId(id), body.date(),
                GoalAmounts.cop(body.amount()), modeOrDefault(body.mode())));
    }

    /** Ajuste de tasa: rige desde la siguiente cuota pendiente. */
    @PostMapping("/rate-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    FinancedGoalDetailResponse adjustRate(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody RateAdjustmentRequest body) {
        return FinancedGoalDetailResponse.from(adjustRate.adjust(owner, new FinancedGoalId(id),
                GoalAmounts.rate(body.ratePercent()), GoalAmounts.copOrNull(body.actualInstallment())));
    }

    /** Deshace un registro; solo se admite el último. */
    @DeleteMapping("/records/{recordId}")
    FinancedGoalDetailResponse undo(
            @CurrentUser UserId owner, @PathVariable UUID id, @PathVariable UUID recordId) {
        return FinancedGoalDetailResponse.from(undoRecord.undo(owner, new FinancedGoalId(id), recordId));
    }

    /** Un abono reduce el plazo salvo que se pida reducir la cuota. */
    private static PrepaymentMode modeOrDefault(PrepaymentMode mode) {
        return mode == null ? PrepaymentMode.REDUCE_TERM : mode;
    }

    /** {@code excessMode} es opcional: cómo aplicar lo que se pague por encima de la cuota. */
    record PaymentRequest(
            @NotNull LocalDate date,
            @Schema(example = "1720956.00") @NotBlank
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String amount,
            PrepaymentMode excessMode) {}

    record PayThroughRequest(@NotNull @Min(1) @Max(FinancedTerms.MAX_INSTALLMENTS) Integer installmentNumber) {}

    /** {@code mode} es opcional: por defecto el abono reduce el plazo. */
    record PrepaymentRequest(
            @NotNull LocalDate date,
            @Schema(example = "5000000.00") @NotBlank
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String amount,
            PrepaymentMode mode) {}

    /** {@code actualInstallment} es opcional: sin ella se conservan los mismos seguros y otros. */
    record RateAdjustmentRequest(
            @Schema(example = "20") @NotBlank
                    @Pattern(regexp = ApiFormats.PERCENT, message = ApiFormats.PERCENT_MESSAGE) String ratePercent,
            @Schema(example = "1802283.00")
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String actualInstallment) {}
}
