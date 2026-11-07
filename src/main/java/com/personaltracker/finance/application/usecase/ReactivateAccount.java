package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactivateAccount {

    private final AccountRepository accounts;
    private final TransactionTotals totals;
    private final Clock clock;

    public ReactivateAccount(AccountRepository accounts, TransactionTotals totals, Clock clock) {
        this.accounts = accounts;
        this.totals = totals;
        this.clock = clock;
    }

    @Transactional
    public AccountView execute(UserId owner, AccountId id) {
        Account account = accounts.require(id, owner);
        account.reactivate(clock.instant());
        accounts.save(account);
        return AccountViews.withBalance(account, totals);
    }
}
