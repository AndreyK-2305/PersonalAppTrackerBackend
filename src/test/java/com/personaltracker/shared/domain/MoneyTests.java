package com.personaltracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void normalizesToScaleTwoSoEqualAmountsAreEqual() {
        assertThat(cop("150000")).isEqualTo(cop("150000.00"));
        assertThat(cop("150000").amount().scale()).isEqualTo(2);
        assertThat(cop("1E+2").amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(cop("10.500")).isEqualTo(cop("10.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "10.555", "123456789012345678", "-123456789012345678"})
    void rejectsAmountsThatDoNotFitNumeric19_2(String amount) {
        assertThatThrownBy(() -> cop(amount))
                .isInstanceOf(InvalidMoneyAmountException.class)
                .extracting("code").isEqualTo("money.amount_invalid");
    }

    @Test
    void acceptsTheLargestAmountThatFits() {
        assertThat(cop("99999999999999999.99").amount()).isEqualByComparingTo("99999999999999999.99");
    }

    @Test
    void addsSubtractsAndNegates() {
        assertThat(cop("100.50").plus(cop("0.50"))).isEqualTo(cop("101"));
        assertThat(cop("100").minus(cop("250.25"))).isEqualTo(cop("-150.25"));
        assertThat(cop("80").negate()).isEqualTo(cop("-80"));
    }

    @Test
    void reportsItsSign() {
        assertThat(cop("1").isPositive()).isTrue();
        assertThat(cop("-1").isNegative()).isTrue();
        assertThat(Money.zero(COP).isZero()).isTrue();
        assertThat(cop("-0.00").isZero()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        Money dollars = new Money(new BigDecimal("1"), USD);

        assertThatThrownBy(() -> cop("1").plus(dollars)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cop("1").minus(dollars)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printsThePlainAmountAndCurrency() {
        assertThat(cop("1E+6")).hasToString("1000000.00 COP");
    }

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }
}
