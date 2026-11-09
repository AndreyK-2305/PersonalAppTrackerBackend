package com.personaltracker.finance.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Producto del usuario, con lo necesario para identificarlo desde otro módulo.
 *
 * @param institution entidad que escribió el usuario; nula en efectivo
 * @param lastFourDigits últimos 4 dígitos; nulos si no se registraron o no aplican
 */
public record ProductSummary(UUID id, Kind kind, String institution, String lastFourDigits) {

    /** Las cuentas de ahorros, corrientes y billeteras digitales son todas de depósito. */
    public enum Kind {
        CASH,
        DEPOSIT,
        CREDIT_CARD
    }

    public ProductSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }
}
