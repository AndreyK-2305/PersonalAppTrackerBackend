package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El producto está archivado y no recibe movimientos nuevos. */
public class AccountArchivedException extends DomainException {

    public AccountArchivedException() {
        super("finance.account.archived", "El producto está archivado. Reactívalo para registrarle movimientos.");
    }
}
