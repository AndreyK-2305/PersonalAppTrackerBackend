package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.InvalidOpeningBalanceException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;

/** Efectivo: activo cuyo saldo inicial no puede ser negativo. */
public final class CashAccount extends Account {

    private CashAccount(
            AccountId id,
            UserId owner,
            String name,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, name, currency, openingBalance, archivedAt, createdAt, updatedAt);
    }

    /** Crea el producto en la moneda del saldo inicial. */
    public static CashAccount open(AccountId id, UserId owner, String name, Money openingBalance, Instant now) {
        Currency currency = SupportedCurrencies.require(openingBalance.currency());
        return new CashAccount(id, owner, validName(name), currency, nonNegative(openingBalance), null, now, now);
    }

    /** Reconstruye un producto persistido tal como estaba. */
    public static CashAccount restore(
            AccountId id,
            UserId owner,
            String name,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new CashAccount(id, owner, name, currency, openingBalance, archivedAt, createdAt, updatedAt);
    }

    public void update(String name, Money openingBalance, Instant now) {
        changeCommon(name, nonNegative(openingBalance), now);
    }

    @Override
    public AccountKind kind() {
        return AccountKind.CASH;
    }

    private static Money nonNegative(Money openingBalance) {
        if (openingBalance.isNegative()) {
            throw InvalidOpeningBalanceException.negativeCash();
        }
        return openingBalance;
    }
}
