package com.personaltracker.identity.domain.model;

import com.personaltracker.identity.domain.exception.InvalidDisplayNameException;
import com.personaltracker.identity.domain.exception.InvalidTimeZoneException;
import com.personaltracker.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Cuenta de usuario. Concentra las reglas de la cuenta: nombre y zona horaria válidos, y bloqueo
 * temporal tras intentos fallidos de inicio de sesión (ADR-0011). La contraseña solo se conoce
 * como hash; verificarla es responsabilidad de la capa de aplicación.
 */
public final class User {

    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("America/Bogota");
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserId id;
    private final Email email;
    private String passwordHash;
    private String displayName;
    private ZoneId timeZone;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(
            UserId id,
            Email email,
            String passwordHash,
            String displayName,
            ZoneId timeZone,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Crea una cuenta nueva con la zona horaria por defecto. */
    public static User register(UserId id, Email email, String passwordHash, String displayName, Instant now) {
        return new User(id, email, passwordHash, validDisplayName(displayName), DEFAULT_TIME_ZONE,
                0, null, null, now, now);
    }

    /** Reconstruye una cuenta persistida tal como estaba. */
    public static User restore(
            UserId id,
            Email email,
            String passwordHash,
            String displayName,
            ZoneId timeZone,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        return new User(id, email, passwordHash, displayName, timeZone, failedLoginAttempts,
                lockedUntil, lastLoginAt, createdAt, updatedAt);
    }

    /** Solo acepta zonas de región (America/Bogota), no desplazamientos fijos como +05:00. */
    public static ZoneId parseTimeZone(String value) {
        if (value == null || !ZoneId.getAvailableZoneIds().contains(value)) {
            throw new InvalidTimeZoneException();
        }
        return ZoneId.of(value);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Registra un intento fallido; al llegar al máximo, bloquea la cuenta temporalmente. */
    public void recordFailedLogin(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            // El bloqueo anterior ya venció: se empieza a contar de nuevo.
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void changePasswordHash(String newPasswordHash, Instant now) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        updatedAt = now;
    }

    public void rename(String newDisplayName, Instant now) {
        displayName = validDisplayName(newDisplayName);
        updatedAt = now;
    }

    public void changeTimeZone(ZoneId newTimeZone, Instant now) {
        timeZone = Objects.requireNonNull(newTimeZone, "newTimeZone");
        updatedAt = now;
    }

    private static String validDisplayName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new InvalidDisplayNameException();
        }
        return trimmed;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public ZoneId timeZone() {
        return timeZone;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
