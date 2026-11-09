package com.personaltracker.capture.domain.model;

/**
 * Entidades con analizador. El nombre visible se compara con la entidad que el usuario escribió en
 * sus productos, sin distinguir mayúsculas ni tildes.
 */
public enum CaptureInstitution {
    BANCOLOMBIA("Bancolombia"),
    RAPPI("Rappi");

    private final String displayName;

    CaptureInstitution(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
