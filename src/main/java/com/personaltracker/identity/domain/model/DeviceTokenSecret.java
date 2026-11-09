package com.personaltracker.identity.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Secreto de un token de dispositivo: 32 bytes aleatorios en Base64 URL, con el prefijo
 * {@value #PREFIX} para reconocerlo si aparece donde no debe. Solo se guarda su SHA-256: el valor se
 * muestra una única vez al crearlo. Un hash sin sal basta porque el secreto es aleatorio y largo, no
 * una contraseña elegida por una persona.
 */
public final class DeviceTokenSecret {

    public static final String PREFIX = "ptd_";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private DeviceTokenSecret() {}

    public static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal; se compara por igualdad en la base de datos. */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible", impossible);
        }
    }
}
