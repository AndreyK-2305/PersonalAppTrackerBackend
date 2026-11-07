package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.AccountList;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.SupportedCurrencies;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Productos del usuario con sus saldos y el patrimonio. Orden: activos, luego archivados y "Otro" al
 * final; dentro de cada grupo, por nombre.
 */
@Service
public class ListAccounts {

    private static final Comparator<Account> ORDER = Comparator
            .comparing(Account::isSystemManaged)
            .thenComparing(Account::isArchived)
            .thenComparing(account -> account.name().toLowerCase(Locale.ROOT));

    private final AccountRepository accounts;
    private final TransactionTotals totals;

    public ListAccounts(AccountRepository accounts, TransactionTotals totals) {
        this.accounts = accounts;
        this.totals = totals;
    }

    @Transactional(readOnly = true)
    public AccountList execute(UserId owner) {
        Map<AccountId, Money> changes = totals.balanceChanges(owner);
        List<AccountView> views = accounts.findAllByOwner(owner).stream()
                .sorted(ORDER)
                .map(account -> AccountView.of(account, changes.getOrDefault(account.id(), zero())))
                .toList();
        Money netWorth = views.stream()
                .filter(view -> view.balance() != null)
                .map(AccountView::balance)
                .reduce(zero(), Money::plus);
        return new AccountList(views, netWorth);
    }

    private static Money zero() {
        return Money.zero(SupportedCurrencies.COP);
    }
}
