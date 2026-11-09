package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** El token de dispositivo no existe o pertenece a otro usuario. */
public class DeviceTokenNotFoundException extends NotFoundException {

    public DeviceTokenNotFoundException() {
        super("identity.device_token_not_found", "El dispositivo no existe.");
    }
}
