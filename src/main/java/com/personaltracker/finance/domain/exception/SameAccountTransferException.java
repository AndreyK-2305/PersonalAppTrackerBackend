package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Una transferencia tiene el mismo producto como origen y destino. */
public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException() {
        super("finance.transfer.same_account", "El producto de origen y el de destino deben ser distintos.");
    }
}
