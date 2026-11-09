package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.api.CapturedTransaction;
import com.personaltracker.finance.api.CapturedTransactions;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.exception.CapturedConversionNotAllowedException;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionOrigin;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Movimientos captados de notificaciones (ADR-0017): los registra con las mismas reglas del dominio
 * que el registro manual, sin categoría y con origen {@code CAPTURE}, y los borra o los convierte
 * desde la revisión. Convertir es borrar y registrar de nuevo, porque el tipo de un movimiento no
 * cambia (ADR-0015).
 */
@Service
public class RecordCapturedTransaction implements CapturedTransactions {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final Clock clock;

    public RecordCapturedTransaction(TransactionRepository transactions, AccountRepository accounts, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID record(UserId owner, CapturedTransaction captured) {
        TransactionId id = TransactionId.newId();
        Instant now = clock.instant();
        TransactionDetails details = new TransactionDetails(
                captured.date(), captured.description(), captured.merchant(), channel(captured.channel()));
        Transaction transaction = switch (captured.kind()) {
            case EXPENSE -> Expense.record(id, owner, accounts.require(account(captured.source()), owner), null,
                    captured.amount(), details, now);
            case INCOME -> Income.record(id, owner, accounts.require(account(captured.destination()), owner), null,
                    captured.amount(), details, now);
            case TRANSFER -> Transfer.record(id, owner, accounts.require(account(captured.source()), owner),
                    accounts.require(account(captured.destination()), owner), captured.amount(), details, now);
        };
        transaction.markCaptured(captured.amountEstimated());
        transactions.save(transaction);
        return id.value();
    }

    @Override
    @Transactional
    public void delete(UserId owner, UUID transactionId) {
        transactions.delete(requireCaptured(owner, transactionId, Transaction.class));
    }

    @Override
    @Transactional
    public UUID convertExpenseToTransfer(UserId owner, UUID expenseId, UUID destination) {
        Expense expense = requireCaptured(owner, expenseId, Expense.class);
        Instant now = clock.instant();
        Transfer transfer = Transfer.record(TransactionId.newId(), owner, accounts.require(expense.source(), owner),
                accounts.require(account(destination), owner), expense.amount(), expense.details(), now);
        return replace(expense, transfer);
    }

    @Override
    @Transactional
    public UUID convertTransferToExpense(UserId owner, UUID transferId) {
        Transfer transfer = requireCaptured(owner, transferId, Transfer.class);
        Instant now = clock.instant();
        Expense expense = Expense.record(TransactionId.newId(), owner, accounts.require(transfer.source(), owner),
                null, transfer.amount(), transfer.details(), now);
        return replace(transfer, expense);
    }

    /** El nuevo movimiento conserva el origen captado y la marca de monto estimado del anterior. */
    private UUID replace(Transaction previous, Transaction replacement) {
        replacement.markCaptured(previous.isAmountEstimated());
        transactions.delete(previous);
        transactions.save(replacement);
        return replacement.id().value();
    }

    /** 404 si no existe o es ajeno; 422 si no es captado o no es del tipo esperado. */
    private <T extends Transaction> T requireCaptured(UserId owner, UUID id, Class<T> type) {
        Transaction transaction = transactions.require(new TransactionId(id), owner);
        if (transaction.origin() != TransactionOrigin.CAPTURE || !type.isInstance(transaction)) {
            throw new CapturedConversionNotAllowedException();
        }
        return type.cast(transaction);
    }

    private static AccountId account(UUID id) {
        return new AccountId(Objects.requireNonNull(id, "Falta el producto del movimiento captado"));
    }

    private static PaymentChannel channel(CapturedTransaction.Channel channel) {
        return switch (channel) {
            case CARD -> PaymentChannel.CARD;
            case BANK_TRANSFER -> PaymentChannel.BANK_TRANSFER;
            case DEPOSIT -> PaymentChannel.DEPOSIT;
        };
    }
}
