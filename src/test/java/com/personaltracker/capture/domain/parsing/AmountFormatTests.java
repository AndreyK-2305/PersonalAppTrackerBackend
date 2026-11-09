package com.personaltracker.capture.domain.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountFormatTests {

    @ParameterizedTest
    @CsvSource({
        "'25.005,00', 25005.00",
        "13.300, 13300",
        "'20,00', 20.00",
        "'1.234.567,89', 1234567.89",
        "100, 100",
    })
    void spanishFormat(String text, BigDecimal expected) {
        assertThat(AmountFormat.SPANISH.parse(text)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "'6,500.00', 6500.00",
        "'480,000', 480000",
        "100.00, 100.00",
        "'1,234,567.89', 1234567.89",
    })
    void englishFormat(String text, BigDecimal expected) {
        assertThat(AmountFormat.ENGLISH.parse(text)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.00", "6,500.00", "1000", "12.30", "1.2345", "", ",50"})
    void spanishRejectsOtherShapes(String text) {
        assertThat(AmountFormat.SPANISH.parse(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"13.300", "25.005,00", "1000", "1,2345", ".50"})
    void englishRejectsOtherShapes(String text) {
        assertThat(AmountFormat.ENGLISH.parse(text)).isEmpty();
    }
}
