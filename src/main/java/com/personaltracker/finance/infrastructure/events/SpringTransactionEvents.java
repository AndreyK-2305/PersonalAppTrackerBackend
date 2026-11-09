package com.personaltracker.finance.infrastructure.events;

import com.personaltracker.finance.api.TransactionDeleted;
import com.personaltracker.finance.application.port.out.TransactionEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica los avisos como eventos de Spring. Los oyentes corren en la misma transacción: si uno
 * falla, el borrado se revierte.
 */
@Component
class SpringTransactionEvents implements TransactionEvents {

    private final ApplicationEventPublisher publisher;

    SpringTransactionEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void transactionDeleted(TransactionDeleted event) {
        publisher.publishEvent(event);
    }
}
