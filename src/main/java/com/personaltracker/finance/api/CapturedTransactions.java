package com.personaltracker.finance.api;

import com.personaltracker.shared.domain.UserId;
import java.util.UUID;

/** Registro de movimientos captados de notificaciones bancarias (ADR-0017). */
public interface CapturedTransactions {

    /**
     * Registra el movimiento con origen {@code CAPTURE} y sin categoría, y devuelve su identificador.
     * Aplica las mismas reglas que un registro manual: los productos deben ser del usuario y estar
     * activos (404 o 422 si no).
     */
    UUID record(UserId owner, CapturedTransaction transaction);
}
