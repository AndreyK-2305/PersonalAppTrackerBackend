package com.personaltracker.identity.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** La contraseña no cumple la política (ADR-0011). */
public class PasswordPolicyViolationException extends DomainException {

    public PasswordPolicyViolationException() {
        super("identity.password_policy", "La contraseña debe tener entre 12 y 64 caracteres. Si usas emojis o símbolos especiales, puede que deba ser más corta.");
    }
}
