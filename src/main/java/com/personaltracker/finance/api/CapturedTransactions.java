package com.personaltracker.finance.api;

import com.personaltracker.shared.domain.UserId;
import java.util.UUID;

/** Movimientos captados de notificaciones bancarias (ADR-0017). */
public interface CapturedTransactions {

    /**
     * Registra el movimiento con origen {@code CAPTURE} y sin categoría, y devuelve su identificador.
     * Aplica las mismas reglas que un registro manual: los productos deben ser del usuario y estar
     * activos (404 o 422 si no).
     */
    UUID record(UserId owner, CapturedTransaction transaction);

    /**
     * Borra un movimiento captado desde la revisión ("descartar"). No publica
     * {@link TransactionDeleted}: quien llama ya actualiza la notificación. 404 si no existe o es
     * ajeno; 422 si no es captado.
     */
    void delete(UserId owner, UUID transactionId);

    /**
     * Reemplaza un gasto captado por una transferencia del mismo producto de origen hacia
     * {@code destination}, con el mismo monto y los mismos detalles ("Es mía"). La categoría se
     * pierde, porque las transferencias no llevan. Devuelve el identificador del nuevo movimiento.
     */
    UUID convertExpenseToTransfer(UserId owner, UUID expenseId, UUID destination);

    /**
     * Reemplaza una transferencia captada por un gasto desde su producto de origen, con el mismo
     * monto y los mismos detalles ("No es mía"). Devuelve el identificador del nuevo movimiento.
     */
    UUID convertTransferToExpense(UserId owner, UUID transferId);
}
