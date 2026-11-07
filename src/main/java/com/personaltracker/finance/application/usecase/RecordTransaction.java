package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.TransactionDraft.AdjustmentDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.application.dto.TransactionView;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra un movimiento manual. Carga los productos y la categoría del propietario (404 si son
 * ajenos) y deja que el dominio valide las reglas del tipo (ADR-0013).
 */
@Service
public class RecordTransaction {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Clock clock;

    public RecordTransaction(
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
    public TransactionView execute(RecordTransactionCommand command) {
        UserId owner = command.owner();
        TransactionId id = TransactionId.newId();
        Instant now = clock.instant();
        Transaction transaction = switch (command.draft()) {
            case IncomeDraft draft -> Income.record(id, owner, accounts.require(draft.destination(), owner),
                    categories.requireIfPresent(draft.category(), owner), draft.amount(), draft.details(), now);
            case ExpenseDraft draft -> Expense.record(id, owner, accounts.require(draft.source(), owner),
                    categories.requireIfPresent(draft.category(), owner), draft.amount(), draft.details(), now);
            case TransferDraft draft -> Transfer.record(id, owner, accounts.require(draft.source(), owner),
                    accounts.require(draft.destination(), owner), draft.amount(), draft.details(), now);
            case AdjustmentDraft draft -> Adjustment.record(id, owner, accounts.require(draft.account(), owner),
                    draft.direction(), draft.amount(), draft.details(), now);
        };
        transactions.save(transaction);
        return TransactionView.of(transaction);
    }
}
