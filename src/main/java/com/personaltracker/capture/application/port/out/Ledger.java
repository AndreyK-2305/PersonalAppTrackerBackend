package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.shared.domain.UserId;
import java.util.UUID;

/** Registro de movimientos en {@code finance}; el adaptador usa {@code finance :: api}. */
public interface Ledger {

    /** Registra el movimiento con origen "captura" y devuelve su identificador. */
    UUID record(UserId owner, CaptureEntry entry);
}
