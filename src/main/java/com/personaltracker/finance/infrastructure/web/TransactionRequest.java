package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.TransactionDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.AdjustmentDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo para registrar o editar un movimiento. Es plano: {@code type} decide qué campos se exigen
 * y los que no aplican al tipo se rechazan (ver docs/api/conventions.md).
 */
@Schema(description = """
        Campos por tipo, además de amount, date y los opcionales description, merchant y channel:
        INCOME: destinationAccountId y categoryId (opcional, de ingreso).
        EXPENSE: sourceAccountId y categoryId (opcional, de gasto).
        TRANSFER: sourceAccountId y destinationAccountId, distintos.
        ADJUSTMENT: accountId y direction.""")
record TransactionRequest(
        @NotNull TransactionType type,
        @Schema(example = "85000.00") @NotBlank @Pattern(regexp = Amounts.UNSIGNED, message = Amounts.MESSAGE)
                String amount,
        @NotNull LocalDate date,
        UUID sourceAccountId,
        UUID destinationAccountId,
        UUID accountId,
        AdjustmentDirection direction,
        UUID categoryId,
        @Size(max = TransactionDetails.MAX_DESCRIPTION_LENGTH) String description,
        @Size(max = TransactionDetails.MAX_MERCHANT_LENGTH) String merchant,
        PaymentChannel channel) {

    /** Valida los campos según el tipo y arma el borrador del caso de uso; si algo falla, responde 400. */
    TransactionDraft toDraft() {
        TypedFields fields = new TypedFields("tipo de movimiento");
        switch (type) {
            case INCOME -> fields.required("destinationAccountId", destinationAccountId)
                    .absent("sourceAccountId", sourceAccountId)
                    .absent("accountId", accountId)
                    .absent("direction", direction);
            case EXPENSE -> fields.required("sourceAccountId", sourceAccountId)
                    .absent("destinationAccountId", destinationAccountId)
                    .absent("accountId", accountId)
                    .absent("direction", direction);
            case TRANSFER -> fields.required("sourceAccountId", sourceAccountId)
                    .required("destinationAccountId", destinationAccountId)
                    .absent("accountId", accountId)
                    .absent("direction", direction)
                    .absent("categoryId", categoryId);
            case ADJUSTMENT -> fields.required("accountId", accountId)
                    .required("direction", direction)
                    .absent("sourceAccountId", sourceAccountId)
                    .absent("destinationAccountId", destinationAccountId)
                    .absent("categoryId", categoryId);
        }
        fields.throwIfAny();
        Money money = Amounts.cop(amount);
        TransactionDetails details = new TransactionDetails(date, description, merchant, channel);
        return switch (type) {
            case INCOME -> new IncomeDraft(account(destinationAccountId), category(), money, details);
            case EXPENSE -> new ExpenseDraft(account(sourceAccountId), category(), money, details);
            case TRANSFER -> new TransferDraft(
                    account(sourceAccountId), account(destinationAccountId), money, details);
            case ADJUSTMENT -> new AdjustmentDraft(account(accountId), direction, money, details);
        };
    }

    private CategoryId category() {
        return categoryId == null ? null : new CategoryId(categoryId);
    }

    private static AccountId account(UUID id) {
        return new AccountId(id);
    }
}
