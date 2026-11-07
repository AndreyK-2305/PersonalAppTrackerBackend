package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.domain.model.AccountKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Producto con su saldo derivado. Los montos son cadenas decimales en {@code currency}. Son nulos los
 * campos que no aplican al tipo: {@code balance} en "Otro" (no lleva saldo); {@code institution} y
 * {@code lastFourDigits} en efectivo y "Otro"; {@code creditLimit}, {@code initialDebt}, {@code debt} y
 * {@code availableCredit} fuera de las tarjetas de crédito.
 */
@Schema(requiredProperties = {"id", "kind", "name", "currency", "openingBalance", "archived", "systemManaged",
        "createdAt", "updatedAt"})
record AccountResponse(
        UUID id,
        AccountKind kind,
        String name,
        String currency,
        String institution,
        String lastFourDigits,
        String openingBalance,
        String balance,
        String creditLimit,
        String initialDebt,
        String debt,
        String availableCredit,
        boolean archived,
        boolean systemManaged,
        Instant createdAt,
        Instant updatedAt) {

    static AccountResponse from(AccountView view) {
        return new AccountResponse(
                view.id().value(),
                view.kind(),
                view.name(),
                view.currency(),
                view.institution(),
                view.lastFourDigits(),
                Amounts.format(view.openingBalance()),
                Amounts.format(view.balance()),
                Amounts.format(view.creditLimit()),
                Amounts.format(view.initialDebt()),
                Amounts.format(view.debt()),
                Amounts.format(view.availableCredit()),
                view.archived(),
                view.systemManaged(),
                view.createdAt(),
                view.updatedAt());
    }
}
