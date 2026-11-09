package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El producto no es una cuenta de depósito activa del usuario. */
public class OwnKeyProductInvalidException extends DomainException {

    public OwnKeyProductInvalidException() {
        super("capture.own_key.product_invalid", "La llave debe llegar a una de tus cuentas o billeteras activas.");
    }
}
