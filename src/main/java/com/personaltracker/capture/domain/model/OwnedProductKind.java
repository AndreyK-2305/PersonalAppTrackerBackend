package com.personaltracker.capture.domain.model;

/** Tipos de producto de {@code finance} que importan para identificar el de una notificación. */
public enum OwnedProductKind {
    CASH,
    DEPOSIT,
    CREDIT_CARD,
    OTHER;

    boolean isOfType(ProductType type) {
        return switch (type) {
            case CREDIT_CARD -> this == CREDIT_CARD;
            case DEPOSIT -> this == DEPOSIT;
        };
    }
}
