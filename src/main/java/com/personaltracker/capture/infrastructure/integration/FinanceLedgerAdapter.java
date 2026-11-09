package com.personaltracker.capture.infrastructure.integration;

import com.personaltracker.capture.application.port.out.Ledger;
import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.finance.api.CapturedTransaction;
import com.personaltracker.finance.api.CapturedTransactions;
import com.personaltracker.shared.domain.UserId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Registro de movimientos captados a través de {@code finance :: api}. */
@Component
class FinanceLedgerAdapter implements Ledger {

    private final CapturedTransactions transactions;

    FinanceLedgerAdapter(CapturedTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public UUID record(UserId owner, CaptureEntry entry) {
        CapturedTransaction.Kind kind = switch (entry.type()) {
            case EXPENSE -> CapturedTransaction.Kind.EXPENSE;
            case INCOME -> CapturedTransaction.Kind.INCOME;
            case TRANSFER -> CapturedTransaction.Kind.TRANSFER;
        };
        CapturedTransaction.Channel channel = switch (entry.channel()) {
            case CARD -> CapturedTransaction.Channel.CARD;
            case BANK_TRANSFER -> CapturedTransaction.Channel.BANK_TRANSFER;
            case DEPOSIT -> CapturedTransaction.Channel.DEPOSIT;
        };
        return transactions.record(owner, new CapturedTransaction(
                kind,
                entry.source().orElse(null),
                entry.destination().orElse(null),
                entry.amount(),
                entry.date(),
                entry.merchant(),
                channel,
                entry.estimatedAmount(),
                entry.description().orElse(null)));
    }
}
