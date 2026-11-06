package com.personaltracker.identity.infrastructure.security;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** {@link PasswordHasher} sobre el {@link PasswordEncoder} delegante de Spring (BCrypt por defecto). */
@Component
class SpringPasswordHasher implements PasswordHasher {

    // BCrypt no admite más de 72 bytes; una entrada más larga nunca puede coincidir.
    private static final int BCRYPT_MAX_BYTES = 72;

    private final PasswordEncoder passwordEncoder;

    SpringPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
