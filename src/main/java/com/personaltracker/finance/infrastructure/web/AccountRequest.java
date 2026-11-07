package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.Institution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo para crear o editar un producto. Es plano: {@code kind} decide qué campos se exigen y los
 * que no aplican al tipo se rechazan (ver docs/api/conventions.md).
 */
@Schema(description = """
        Campos por tipo:
        CASH: name y openingBalance (0 si se omite).
        SAVINGS, CHECKING y DIGITAL_WALLET: name, institution, lastFourDigits (opcional) y openingBalance \
        (0 si se omite; admite negativos).
        CREDIT_CARD: name, institution, lastFourDigits (opcional), creditLimit e initialDebt (0 si se omite).
        OTHER no se puede crear: lo crea la bienvenida de Finanzas.""")
record AccountRequest(
        @NotNull AccountKind kind,
        @NotBlank @Size(max = Account.MAX_NAME_LENGTH) String name,
        @Size(max = Institution.MAX_LENGTH) String institution,
        @Pattern(regexp = "^[0-9]{4}$", message = "deben ser exactamente 4 números") String lastFourDigits,
        @Schema(example = "150000.00") @Pattern(regexp = Amounts.SIGNED, message = Amounts.MESSAGE)
                String openingBalance,
        @Schema(example = "5000000.00") @Pattern(regexp = Amounts.UNSIGNED, message = Amounts.MESSAGE)
                String creditLimit,
        @Schema(example = "0.00") @Pattern(regexp = Amounts.UNSIGNED, message = Amounts.MESSAGE)
                String initialDebt) {

    private static final String ZERO = "0";

    /** Valida los campos según el tipo y arma los datos del caso de uso; si algo falla, responde 400. */
    AccountDetails toDetails() {
        TypedFields fields = new TypedFields("tipo de producto");
        switch (kind) {
            case CASH -> fields.absent("institution", institution)
                    .absent("lastFourDigits", lastFourDigits)
                    .absent("creditLimit", creditLimit)
                    .absent("initialDebt", initialDebt);
            case SAVINGS, CHECKING, DIGITAL_WALLET -> fields.required("institution", institution)
                    .absent("creditLimit", creditLimit)
                    .absent("initialDebt", initialDebt);
            case CREDIT_CARD -> fields.required("institution", institution)
                    .required("creditLimit", creditLimit)
                    .absent("openingBalance", openingBalance);
            case OTHER -> fields.reject("kind", "el producto «Otro» lo crea la bienvenida de Finanzas");
        }
        fields.throwIfAny();
        return switch (kind) {
            case CASH -> new AccountDetails.Cash(name, Amounts.copOr(openingBalance, ZERO));
            case SAVINGS, CHECKING, DIGITAL_WALLET -> new AccountDetails.Deposit(
                    kind, name, institution, lastFourDigits, Amounts.copOr(openingBalance, ZERO));
            case CREDIT_CARD -> new AccountDetails.CreditCard(
                    name, institution, lastFourDigits, Amounts.cop(creditLimit), Amounts.copOr(initialDebt, ZERO));
            case OTHER -> throw new IllegalStateException("OTHER ya fue rechazado");
        };
    }
}
