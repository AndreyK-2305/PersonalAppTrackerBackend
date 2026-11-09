package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.shared.domain.UserId;
import java.util.UUID;

/** Movimientos captados en {@code finance}; el adaptador usa {@code finance :: api}. */
public interface Ledger {

    /** Registra el movimiento con origen "captura" y devuelve su identificador. */
    UUID record(UserId owner, CaptureEntry entry);

    /** Borra un movimiento captado al descartar su notificación desde la revisión. */
    void delete(UserId owner, UUID transactionId);

    /** "Es mía": reemplaza el gasto por una transferencia hacia {@code destination}; devuelve el nuevo identificador. */
    UUID convertExpenseToTransfer(UserId owner, UUID expenseId, UUID destination);

    /** "No es mía": reemplaza la transferencia por un gasto; devuelve el nuevo identificador. */
    UUID convertTransferToExpense(UserId owner, UUID transferId);
}
