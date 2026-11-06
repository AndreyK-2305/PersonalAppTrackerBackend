package com.personaltracker.identity.application.port.out;

/** Indica si se permite registrar cuentas nuevas (REGISTRATION_ENABLED, ADR-0012). */
public interface RegistrationPolicy {

    boolean isRegistrationOpen();
}
