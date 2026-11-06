package com.personaltracker.identity.application.port.out;

/** Hash y verificación de contraseñas. La implementación decide el algoritmo (ADR-0004). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
