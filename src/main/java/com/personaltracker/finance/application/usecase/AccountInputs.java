package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.domain.model.Institution;
import com.personaltracker.finance.domain.model.LastFourDigits;

/** Convierte los textos de entrada en objetos de valor del dominio, que hacen la validación. */
final class AccountInputs {

    private AccountInputs() {
    }

    static Institution institution(String value) {
        return new Institution(value);
    }

    /** Los últimos dígitos son opcionales: un texto vacío equivale a no indicarlos. */
    static LastFourDigits lastFourDigits(String value) {
        return value == null || value.isBlank() ? null : new LastFourDigits(value);
    }
}
