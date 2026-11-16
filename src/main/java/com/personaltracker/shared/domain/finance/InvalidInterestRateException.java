package com.personaltracker.shared.domain.finance;

import com.personaltracker.shared.domain.DomainException;

/** La tasa es negativa, supera el 1000 % EA o tiene más de 4 decimales en porcentaje. */
public class InvalidInterestRateException extends DomainException {

    public InvalidInterestRateException() {
        super("rate.invalid", "La tasa debe estar entre 0 % y 1000 % EA, con hasta 4 decimales.");
    }
}
