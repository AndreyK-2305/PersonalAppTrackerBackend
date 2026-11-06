package com.personaltracker.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTests {

    @Test
    void normalizesSpacesAndCase() {
        assertThat(new Email("  Kevin.Jaimes@Example.COM ").value()).isEqualTo("kevin.jaimes@example.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "sin-arroba", "a@b", "con espacio@correo.co", "doble@@correo.co"})
    void rejectsInvalidFormats(String value) {
        assertThatThrownBy(() -> new Email(value)).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejectsAddressesLongerThan254Characters() {
        String tooLong = "a".repeat(250) + "@x.co";

        assertThatThrownBy(() -> new Email(tooLong)).isInstanceOf(InvalidEmailException.class);
    }
}
