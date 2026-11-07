package com.personaltracker.finance.application.dto;

/** Página solicitada: {@code page} empieza en 0 y {@code size} va de 1 a {@value #MAX_SIZE}. */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Página inválida: page=" + page + ", size=" + size);
        }
    }
}
