package com.personaltracker.shared.infrastructure.web;

/**
 * Formatos de los valores que viajan como texto en la API (ADR-0006): montos como cadenas decimales
 * con hasta 2 decimales y porcentajes con hasta 4. Los usan las anotaciones de validación de los
 * adaptadores web de cada módulo.
 */
public final class ApiFormats {

    /** Monto no negativo, por ejemplo {@code "150000.50"}. */
    public static final String AMOUNT = "^[0-9]{1,17}(\\.[0-9]{1,2})?$";

    /** Monto con signo opcional. */
    public static final String SIGNED_AMOUNT = "^-?[0-9]{1,17}(\\.[0-9]{1,2})?$";

    public static final String AMOUNT_MESSAGE = "debe ser un monto con hasta 2 decimales, por ejemplo 150000.50";

    /** Porcentaje no negativo, por ejemplo {@code "10.5"}. */
    public static final String PERCENT = "^[0-9]{1,4}(\\.[0-9]{1,4})?$";

    public static final String PERCENT_MESSAGE = "debe ser un porcentaje con hasta 4 decimales, por ejemplo 10.5";

    private ApiFormats() {
    }
}
