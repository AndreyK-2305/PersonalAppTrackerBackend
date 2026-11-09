package com.personaltracker.capture.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Tasa de cambio oficial (TRM) para convertir compras en dólares a COP (ADR-0017). */
public interface ExchangeRates {

    /**
     * Pesos por dólar vigentes en la fecha: la TRM de ese día o, si no hay, la más reciente anterior.
     * Vacío si la fuente no responde o no tiene datos.
     */
    Optional<BigDecimal> copPerUsd(LocalDate date);
}
