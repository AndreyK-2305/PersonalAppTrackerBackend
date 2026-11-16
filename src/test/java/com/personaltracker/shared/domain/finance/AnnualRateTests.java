package com.personaltracker.shared.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnnualRateTests {

    @Test
    void convertsAPercentToAFractionWithSixDecimals() {
        AnnualRate rate = AnnualRate.ofPercent(new BigDecimal("24.5"));

        assertThat(rate.value()).isEqualByComparingTo("0.245");
        assertThat(rate.value().scale()).isEqualTo(6);
        assertThat(rate.percent()).isEqualByComparingTo("24.5");
        assertThat(rate.percent().scale()).isEqualTo(4);
    }

    @Test
    void monthlyRateOfTwelvePercentEaIsTheTwelfthRootMinusOne() {
        BigDecimal monthly = AnnualRate.ofPercent(new BigDecimal("12")).monthly();

        // 1,12^(1/12) − 1 = 0,0094887929345830...
        assertThat(monthly.setScale(16, RoundingMode.HALF_EVEN)).isEqualByComparingTo("0.0094887929345830");
        BigDecimal backToAnnual = FinancialMath.growth(monthly, 12).subtract(BigDecimal.ONE);
        assertThat(backToAnnual.subtract(new BigDecimal("0.12")).abs()).isLessThan(new BigDecimal("1E-28"));
    }

    @Test
    void aZeroRateHasAZeroMonthlyRate() {
        assertThat(AnnualRate.ZERO.isZero()).isTrue();
        assertThat(AnnualRate.ZERO.monthly()).isEqualByComparingTo("0");
    }

    @Test
    void theMaximumRateStillConverges() {
        BigDecimal monthly = AnnualRate.ofPercent(new BigDecimal("1000")).monthly();

        BigDecimal backToAnnual = FinancialMath.growth(monthly, 12).subtract(BigDecimal.ONE);
        assertThat(backToAnnual.subtract(BigDecimal.TEN).abs()).isLessThan(new BigDecimal("1E-26"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1000.0001", "12.12345"})
    void rejectsNegativeTooHighOrTooPreciseRates(String percent) {
        assertThatThrownBy(() -> AnnualRate.ofPercent(new BigDecimal(percent)))
                .isInstanceOf(InvalidInterestRateException.class);
    }
}
