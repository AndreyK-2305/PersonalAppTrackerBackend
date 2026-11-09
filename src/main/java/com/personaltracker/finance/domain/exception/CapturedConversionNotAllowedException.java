package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Se pidió borrar o convertir desde la revisión un movimiento que no es captado o no es del tipo esperado. */
public class CapturedConversionNotAllowedException extends DomainException {

    public CapturedConversionNotAllowedException() {
        super("finance.transaction.capture_change_invalid",
                "Solo se puede cambiar desde la revisión un movimiento captado del tipo esperado.");
    }
}
