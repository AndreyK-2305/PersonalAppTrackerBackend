package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import java.util.Objects;

/** Cambio que produce un movimiento en el saldo de un producto: positivo si entra dinero. */
public record BalanceEffect(AccountId account, Money change) {

    public BalanceEffect {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(change, "change");
    }
}
