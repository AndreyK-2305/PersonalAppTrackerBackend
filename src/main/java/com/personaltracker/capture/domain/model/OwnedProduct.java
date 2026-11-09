package com.personaltracker.capture.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Producto activo del usuario, tal como lo ve {@code capture}: solo lo necesario para identificarlo.
 * Lo entrega {@code finance} a través de su API; aquí no se conocen saldos ni movimientos.
 *
 * @param institution entidad que escribió el usuario; vacía en efectivo y "Otro"
 */
public record OwnedProduct(UUID id, OwnedProductKind kind, Optional<String> institution, Optional<String> lastFourDigits) {

    public OwnedProduct {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(institution, "institution");
        Objects.requireNonNull(lastFourDigits, "lastFourDigits");
    }
}
