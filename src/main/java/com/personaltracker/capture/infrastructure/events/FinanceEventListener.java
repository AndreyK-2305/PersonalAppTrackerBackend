package com.personaltracker.capture.infrastructure.events;

import com.personaltracker.capture.application.usecase.DiscardDeletedTransaction;
import com.personaltracker.finance.api.TransactionDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reacciona a los avisos de {@code finance :: api}; corre dentro de la transacción del borrado. */
@Component
class FinanceEventListener {

    private final DiscardDeletedTransaction discardDeletedTransaction;

    FinanceEventListener(DiscardDeletedTransaction discardDeletedTransaction) {
        this.discardDeletedTransaction = discardDeletedTransaction;
    }

    @EventListener
    void on(TransactionDeleted event) {
        discardDeletedTransaction.execute(event.owner(), event.transactionId());
    }
}
