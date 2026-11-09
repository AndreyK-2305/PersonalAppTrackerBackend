package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.OwnerName;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;

/** Datos del perfil del usuario que necesita la captura; el adaptador usa {@code identity :: api}. */
public interface CaptureOwners {

    /** Zona horaria en la que se leen las fechas de los textos. */
    ZoneId timeZone(UserId owner);

    /** Nombres y apellidos; vacío si el usuario no los ha configurado. */
    OwnerName name(UserId owner);
}
