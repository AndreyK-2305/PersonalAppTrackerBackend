package com.personaltracker.finance.api;

import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.UUID;

/** Productos del usuario para otros módulos, sin saldos (ADR-0017). */
public interface FinanceProducts {

    /** Productos activos (no archivados) del usuario, sin el producto de sistema "Otro". */
    List<ProductSummary> activeProducts(UserId owner);

    /** El producto de sistema "Otro" del usuario; lo crea si todavía no existe. */
    UUID otherProduct(UserId owner);
}
