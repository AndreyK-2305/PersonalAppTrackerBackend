package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El texto de la notificación ya se borró (45 días) y no se puede volver a analizar. */
public class RawTextExpiredException extends DomainException {

    public RawTextExpiredException() {
        super("capture.review.text_expired", "El texto de esta notificación ya se borró. Registra el movimiento a mano.");
    }
}
