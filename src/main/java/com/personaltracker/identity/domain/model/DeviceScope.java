package com.personaltracker.identity.domain.model;

/**
 * Permiso de un token de dispositivo, por herramienta (ADR-0017). Un token solo abre los endpoints
 * de dispositivo de sus permisos; nunca abre sesión ni el resto de la API.
 */
public enum DeviceScope {
    /** Enviar notificaciones bancarias a la captura de Finanzas. */
    FINANCE_CAPTURE("finance:capture");

    private final String value;

    DeviceScope(String value) {
        this.value = value;
    }

    /** Nombre estable del permiso, por ejemplo {@code finance:capture}. */
    public String value() {
        return value;
    }
}
