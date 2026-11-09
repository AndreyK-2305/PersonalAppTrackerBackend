package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.api.TransactionDeleted;
import com.personaltracker.finance.application.port.out.TransactionEvents;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionOrigin;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra un movimiento de forma definitiva, sin historial (ADR-0015). Si era captado de una
 * notificación, avisa con {@link TransactionDeleted} para que la notificación quede descartada.
 */
@Service
public class DeleteTransaction {

    private final TransactionRepository transactions;
    private final TransactionEvents events;

    public DeleteTransaction(TransactionRepository transactions, TransactionEvents events) {
        this.transactions = transactions;
        this.events = events;
    }

    @Transactional
    public void execute(UserId owner, TransactionId id) {
        Transaction transaction = transactions.require(id, owner);
        transactions.delete(transaction);
        if (transaction.origin() == TransactionOrigin.CAPTURE) {
            events.transactionDeleted(new TransactionDeleted(owner, id.value()));
        }
    }
}
