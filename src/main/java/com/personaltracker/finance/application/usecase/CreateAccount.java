package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.usecase.AccountInputs.institution;
import static com.personaltracker.finance.application.usecase.AccountInputs.lastFourDigits;

import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.domain.exception.AccountNameTakenException;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crea un producto. El nombre es único por usuario (ADR-0014). */
@Service
public class CreateAccount {

    private final AccountRepository accounts;
    private final Clock clock;

    public CreateAccount(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public AccountView execute(CreateAccountCommand command) {
        Instant now = clock.instant();
        AccountId id = AccountId.newId();
        Account account = switch (command.details()) {
            case AccountDetails.Cash cash ->
                    CashAccount.open(id, command.owner(), cash.name(), cash.openingBalance(), now);
            case AccountDetails.Deposit deposit -> DepositAccount.open(id, command.owner(), deposit.kind(),
                    deposit.name(), institution(deposit.institution()), lastFourDigits(deposit.lastFourDigits()),
                    deposit.openingBalance(), now);
            case AccountDetails.CreditCard card -> CreditCardAccount.open(id, command.owner(), card.name(),
                    institution(card.institution()), lastFourDigits(card.lastFourDigits()), card.creditLimit(),
                    card.initialDebt(), now);
        };
        if (accounts.existsByOwnerAndName(command.owner(), account.name(), null)) {
            throw new AccountNameTakenException();
        }
        accounts.save(account);
        return AccountView.of(account, Money.zero(account.currency()));
    }
}
