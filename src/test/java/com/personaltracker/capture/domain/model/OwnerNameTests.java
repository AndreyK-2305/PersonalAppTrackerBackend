package com.personaltracker.capture.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OwnerNameTests {

    private final OwnerName owner = OwnerName.of("Ana María", "Pérez Gómez");

    @ParameterizedTest
    @ValueSource(strings = {
        "Ana Maria Perez Gomez",
        "ANA MARÍA PÉREZ GÓMEZ",
        "Maria Perez",
        "Ana Perez",
        "Ana Maria Gomez",
        "Gomez Ana",
        "  ana   maría\npérez ",
    })
    void matchesCombinationsWithAtLeastOneGivenNameAndOneSurname(String candidate) {
        assertThat(owner.matches(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Ana",
        "Ana Maria",
        "Perez Gomez",
        "Ana Lopez",
        "Ana Maria Perez Lopez",
        "Carlos Andres Ruiz Diaz",
        "",
    })
    void doesNotMatchWithoutBothPartsOrWithForeignWords(String candidate) {
        assertThat(owner.matches(candidate)).isFalse();
    }

    @Test
    void anIncompleteProfileNeverMatches() {
        assertThat(OwnerName.of("Ana", "").matches("Ana")).isFalse();
        assertThat(OwnerName.of("", "Perez").matches("Perez")).isFalse();
        assertThat(OwnerName.of(null, null).isEmpty()).isTrue();
    }
}
