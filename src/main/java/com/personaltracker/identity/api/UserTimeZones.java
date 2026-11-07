package com.personaltracker.identity.api;

import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;

/**
 * Zona horaria del perfil de cada usuario. Otros módulos la usan para calcular fechas locales, como
 * "hoy" o los límites de un periodo de reporte (ADR-0006).
 */
public interface UserTimeZones {

    /** Falla con 404 ({@code identity.user_not_found}) si la cuenta no existe. */
    ZoneId of(UserId user);
}
