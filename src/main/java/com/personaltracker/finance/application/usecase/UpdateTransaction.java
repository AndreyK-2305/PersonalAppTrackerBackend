package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.TransactionDraft.AdjustmentDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.application.dto.TransactionView;
import com.personaltracker.finance.application.dto.UpdateTransactionCommand;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.exception.TransactionTypeChangeException;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edita un movimiento con un borrador de su mismo tipo; para cambiar el tipo hay que borrarlo y
 * registrarlo de nuevo (ADR-0015).
 */
@Service
public class UpdateTransaction {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Clock clock;

    public UpdateTransaction(
            TransactionRepository transactions,
            AccountRepository accounts,
            CategoryRepository categories,
            Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public TransactionView execute(UpdateTransactionCommand command) {
        UserId owner = command.owner();
        Transaction transaction = transactions.require(command.transaction(), owner);
        Instant now = clock.instant();
        switch (transaction) {
            case Income income when command.draft() instanceof IncomeDraft draft ->
                    income.revise(accounts.require(draft.destination(), owner),
                            categories.requireIfPresent(draft.category(), owner), draft.amount(), draft.details(), now);
            case Expense expense when command.draft() instanceof ExpenseDraft draft ->
                    expense.revise(accounts.require(draft.source(), owner),
                            categories.requireIfPresent(draft.category(), owner), draft.amount(), draft.details(), now);
            case Transfer transfer when command.draft() instanceof TransferDraft draft ->
                    transfer.revise(accounts.require(draft.source(), owner), accounts.require(draft.destination(), owner),
                            draft.amount(), draft.details(), now);
            case Adjustment adjustment when command.draft() instanceof AdjustmentDraft draft ->
                    adjustment.revise(accounts.require(draft.account(), owner), draft.direction(), draft.amount(),
                            draft.details(), now);
            default -> throw new TransactionTypeChangeException();
        }
        transactions.save(transaction);
        return TransactionView.of(transaction);
    }
}
