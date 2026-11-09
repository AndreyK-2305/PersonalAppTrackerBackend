package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La acción de revisión no corresponde al estado o al motivo de la notificación. */
public class ReviewActionNotAllowedException extends DomainException {

    public ReviewActionNotAllowedException() {
        super("capture.review.action_invalid", "Esta acción no aplica a esta notificación.");
    }
}
