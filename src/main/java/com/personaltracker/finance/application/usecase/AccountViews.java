package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.shared.domain.Money;

/** Arma la vista de un producto con su saldo actual. */
final class AccountViews {

    private AccountViews() {
    }

    static AccountView withBalance(Account account, TransactionTotals totals) {
        Money change = totals.balanceChanges(account.owner())
                .getOrDefault(account.id(), Money.zero(account.currency()));
        return AccountView.of(account, change);
    }
}
