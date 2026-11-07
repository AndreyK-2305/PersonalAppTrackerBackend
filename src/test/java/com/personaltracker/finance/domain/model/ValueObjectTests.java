package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.domain.exception.InvalidDescriptionException;
import com.personaltracker.finance.domain.exception.InvalidInstitutionException;
import com.personaltracker.finance.domain.exception.InvalidLastFourDigitsException;
import com.personaltracker.finance.domain.exception.InvalidMerchantException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectTests {

    @Test
    void institutionIsTrimmed() {
        assertThat(new Institution("  Bancolombia ").name()).isEqualTo("Bancolombia");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void institutionIsRequired(String name) {
        assertThatThrownBy(() -> new Institution(name)).isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void institutionRejectsMoreThan60Characters() {
        assertThatThrownBy(() -> new Institution("x".repeat(61))).isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void lastFourDigitsAreTrimmed() {
        assertThat(new LastFourDigits(" 5403 ").value()).isEqualTo("5403");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "540", "54031", "54a3", "4111111111111111", "٥٤٠٣"})
    void lastFourDigitsMustBeExactlyFourAsciiDigits(String value) {
        assertThatThrownBy(() -> new LastFourDigits(value))
                .isInstanceOf(InvalidLastFourDigitsException.class)
                .extracting("code").isEqualTo("finance.account.last_four_invalid");
    }

    @Test
    void detailsTrimTextsAndStoreBlanksAsNull() {
        TransactionDetails details = new TransactionDetails(TODAY, "  Almuerzo ", "   ", PaymentChannel.CARD);

        assertThat(details.description()).isEqualTo("Almuerzo");
        assertThat(details.merchant()).isNull();
        assertThat(details.channel()).isEqualTo(PaymentChannel.CARD);
    }

    @Test
    void detailsLimitDescriptionAndMerchantLength() {
        assertThatThrownBy(() -> new TransactionDetails(TODAY, "x".repeat(201), null, null))
                .isInstanceOf(InvalidDescriptionException.class);
        assertThatThrownBy(() -> new TransactionDetails(TODAY, null, "x".repeat(101), null))
                .isInstanceOf(InvalidMerchantException.class);
    }

    @Test
    void detailsRequireADate() {
        assertThatThrownBy(() -> new TransactionDetails(null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
