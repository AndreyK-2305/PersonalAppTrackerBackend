package com.personaltracker.identity.domain.model;

import com.personaltracker.identity.domain.exception.InvalidEmailException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Correo electrónico normalizado (sin espacios y en minúsculas): es el identificador de inicio de sesión. */
public record Email(String value) {

    public static final int MAX_LENGTH = 254;

    // Validación estructural deliberadamente simple; la verificación real llegará con el envío de correos.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new InvalidEmailException();
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException();
        }
    }
}
