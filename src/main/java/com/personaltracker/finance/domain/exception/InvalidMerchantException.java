package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El nombre del comercio es demasiado largo. */
public class InvalidMerchantException extends DomainException {

    public InvalidMerchantException() {
        super("finance.transaction.merchant_invalid", "El comercio puede tener como máximo 100 caracteres.");
    }
}
