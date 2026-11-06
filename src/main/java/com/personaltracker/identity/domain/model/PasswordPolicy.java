package com.personaltracker.identity.domain.model;

import com.personaltracker.identity.domain.exception.PasswordPolicyViolationException;
import java.nio.charset.StandardCharsets;

/**
 * Política de contraseñas (ADR-0011): longitud entre 12 y 64 caracteres, sin reglas de
 * composición, y como máximo 72 bytes en UTF-8 porque BCrypt ignora lo que sigue.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void check(String rawPassword) {
        if (rawPassword == null) {
            throw new PasswordPolicyViolationException();
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        int bytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_LENGTH || length > MAX_LENGTH || bytes > MAX_BYTES) {
            throw new PasswordPolicyViolationException();
        }
    }
}
