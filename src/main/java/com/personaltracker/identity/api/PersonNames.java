package com.personaltracker.identity.api;

import com.personaltracker.shared.domain.UserId;

/** Nombres y apellidos del perfil de cada usuario, para otros módulos (ADR-0017). */
public interface PersonNames {

    /** Falla con 404 ({@code identity.user_not_found}) si la cuenta no existe. */
    Names of(UserId user);

    /** Nombres y apellidos; cada uno es nulo si el usuario no lo ha configurado. */
    record Names(String givenNames, String surnames) {}
}
