package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.exception.AccountInUseException;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Borra un producto sin movimientos; uno con movimientos se archiva (ADR-0014). */
@Service
public class DeleteAccount {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public DeleteAccount(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public void execute(UserId owner, AccountId id) {
        Account account = accounts.require(id, owner);
        account.ensureUserManaged();
        if (transactions.existsByAccount(account.id(), owner)) {
            throw new AccountInUseException();
        }
        accounts.delete(account);
    }
}
