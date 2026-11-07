package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.usecase.AccountInputs.institution;
import static com.personaltracker.finance.application.usecase.AccountInputs.lastFourDigits;

import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.UpdateAccountCommand;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.exception.AccountKindChangeException;
import com.personaltracker.finance.domain.exception.AccountNameTakenException;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.OtherAccount;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reemplaza los datos editables de un producto. El tipo no cambia, salvo entre cuentas de depósito
 * (ahorros, corriente y billetera); "Otro" no se edita (ADR-0014).
 */
@Service
public class UpdateAccount {

    private final AccountRepository accounts;
    private final TransactionTotals totals;
    private final Clock clock;

    public UpdateAccount(AccountRepository accounts, TransactionTotals totals, Clock clock) {
        this.accounts = accounts;
        this.totals = totals;
        this.clock = clock;
    }

    @Transactional
    public AccountView execute(UpdateAccountCommand command) {
        Account account = accounts.require(command.account(), command.owner());
        Instant now = clock.instant();
        switch (account) {
            case OtherAccount other -> other.ensureUserManaged();
            case CashAccount cash when command.details() instanceof AccountDetails.Cash details ->
                    cash.update(details.name(), details.openingBalance(), now);
            case DepositAccount deposit when command.details() instanceof AccountDetails.Deposit details ->
                    deposit.update(details.kind(), details.name(), institution(details.institution()),
                            lastFourDigits(details.lastFourDigits()), details.openingBalance(), now);
            case CreditCardAccount card when command.details() instanceof AccountDetails.CreditCard details ->
                    card.update(details.name(), institution(details.institution()),
                            lastFourDigits(details.lastFourDigits()), details.creditLimit(), details.initialDebt(),
                            now);
            default -> throw new AccountKindChangeException();
        }
        if (accounts.existsByOwnerAndName(command.owner(), account.name(), account.id())) {
            throw new AccountNameTakenException();
        }
        accounts.save(account);
        return AccountViews.withBalance(account, totals);
    }
}
