package com.personaltracker.capture.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La notificación no existe o pertenece a otro usuario. */
public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException() {
        super("capture.notification.not_found", "La notificación no existe.");
    }
}
