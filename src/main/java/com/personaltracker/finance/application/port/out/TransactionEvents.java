package com.personaltracker.finance.application.port.out;

import com.personaltracker.finance.api.TransactionDeleted;

/** Avisos de finance a otros módulos, sin que finance los conozca. */
public interface TransactionEvents {

    void transactionDeleted(TransactionDeleted event);
}
