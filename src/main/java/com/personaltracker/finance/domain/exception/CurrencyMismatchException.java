package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Un monto y un producto, o dos productos, tienen monedas distintas. */
public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException() {
        super("finance.currency.mismatch", "Los montos y los productos deben estar en la misma moneda.");
    }
}
