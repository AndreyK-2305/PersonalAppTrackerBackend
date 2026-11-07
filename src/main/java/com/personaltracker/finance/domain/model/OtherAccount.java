package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.AccountNotAllowedException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;

/**
 * Producto de sistema "Otro" (ADR-0014): recibe los movimientos cuyo producto no se identifica. Hay
 * uno por usuario y no se puede editar, archivar ni borrar. Solo admite ingresos y gastos, que
 * cuentan en los reportes; no lleva saldo ni entra en el patrimonio.
 */
public final class OtherAccount extends Account {

    public static final String NAME = "Otro";

    private OtherAccount(AccountId id, UserId owner, Currency currency, Instant createdAt, Instant updatedAt) {
        super(id, owner, NAME, currency, Money.zero(currency), null, createdAt, updatedAt);
    }

    public static OtherAccount createFor(AccountId id, UserId owner, Currency currency, Instant now) {
        return new OtherAccount(id, owner, SupportedCurrencies.require(currency), now, now);
    }

    /** Reconstruye el producto persistido tal como estaba. */
    public static OtherAccount restore(
            AccountId id, UserId owner, Currency currency, Instant createdAt, Instant updatedAt) {
        return new OtherAccount(id, owner, currency, createdAt, updatedAt);
    }

    @Override
    public AccountKind kind() {
        return AccountKind.OTHER;
    }

    @Override
    public boolean tracksBalance() {
        return false;
    }

    @Override
    public boolean isSystemManaged() {
        return true;
    }

    /** Las transferencias y los ajustes mueven saldos, y este producto no lleva saldo. */
    @Override
    public void ensureAccepts(TransactionType type) {
        if (type == TransactionType.TRANSFER || type == TransactionType.ADJUSTMENT) {
            throw new AccountNotAllowedException();
        }
        super.ensureAccepts(type);
    }
}
