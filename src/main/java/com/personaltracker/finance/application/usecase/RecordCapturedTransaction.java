package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.api.CapturedTransaction;
import com.personaltracker.finance.api.CapturedTransactions;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra un movimiento captado de una notificación (ADR-0017). Usa las mismas reglas del dominio
 * que el registro manual, sin categoría, y lo marca con origen {@code CAPTURE}.
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
