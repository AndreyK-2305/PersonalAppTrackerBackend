package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Borra un movimiento de forma definitiva, sin historial (ADR-0015). */
@Service
public class DeleteTransaction {

    private final TransactionRepository transactions;

    public DeleteTransaction(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional
    public void execute(UserId owner, TransactionId id) {
        transactions.delete(transactions.require(id, owner));
    }
}
