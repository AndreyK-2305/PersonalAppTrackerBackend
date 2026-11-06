package com.personaltracker.identity.infrastructure.config;

import com.personaltracker.identity.application.port.out.RegistrationPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración de identity. {@code app.identity.registration-enabled} = REGISTRATION_ENABLED (ADR-0012). */
@ConfigurationProperties("app.identity")
record IdentityProperties(boolean registrationEnabled) implements RegistrationPolicy {

    @Override
    public boolean isRegistrationOpen() {
        return registrationEnabled;
    }
}
