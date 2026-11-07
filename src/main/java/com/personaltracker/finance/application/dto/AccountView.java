package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.LastFourDigits;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.shared.domain.Money;
import java.time.Instant;

/**
 * Producto con su saldo derivado (ADR-0013). Los campos que no aplican al tipo son nulos:
 * {@code balance} en "Otro"; {@code institution} y {@code lastFourDigits} en efectivo y "Otro";
 * {@code creditLimit}, {@code initialDebt}, {@code debt} y {@code availableCredit} fuera de las tarjetas.
 */
public record AccountView(
        AccountId id,
        AccountKind kind,
        String name,
        String currency,
        String institution,
        String lastFourDigits,
        Money openingBalance,
        Money balance,
        Money creditLimit,
        Money initialDebt,
        Money debt,
        Money availableCredit,
        boolean archived,
        boolean systemManaged,
        Instant createdAt,
        Instant updatedAt) {

    /** {@code balanceChange} es la suma de los efectos de sus movimientos (cero si no tiene). */
    public static AccountView of(Account account, Money balanceChange) {
        Money balance = account.tracksBalance() ? account.openingBalance().plus(balanceChange) : null;
        return switch (account) {
            case CashAccount cash -> base(cash, balance, null, null, null, null, null, null);
            case DepositAccount deposit -> base(deposit, balance, deposit.institution().name(),
                    digits(deposit.lastFourDigits()), null, null, null, null);
            case CreditCardAccount card -> base(card, balance, card.institution().name(),
                    digits(card.lastFourDigits()), card.creditLimit(), card.initialDebt(), card.debtFor(balance),
                    card.availableCreditFor(balance));
            case OtherAccount other -> base(other, null, null, null, null, null, null, null);
        };
    }

    private static AccountView base(
            Account account,
            Money balance,
            String institution,
            String lastFourDigits,
            Money creditLimit,
            Money initialDebt,
            Money debt,
            Money availableCredit) {
        return new AccountView(
                account.id(),
                account.kind(),
                account.name(),
                account.currency().getCurrencyCode(),
                institution,
                lastFourDigits,
                account.openingBalance(),
                balance,
                creditLimit,
                initialDebt,
                debt,
                availableCredit,
                account.isArchived(),
                account.isSystemManaged(),
                account.createdAt(),
                account.updatedAt());
    }

    private static String digits(LastFourDigits lastFourDigits) {
        return lastFourDigits == null ? null : lastFourDigits.value();
    }
}
