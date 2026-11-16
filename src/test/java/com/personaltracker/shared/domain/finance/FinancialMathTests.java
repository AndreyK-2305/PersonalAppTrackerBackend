package com.personaltracker.shared.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialMathTests {

    @Test
    void rootsAreExactForPerfectPowers() {
        assertThat(FinancialMath.root(new BigDecimal("4096"), 12).subtract(BigDecimal.TWO).abs())
                .isLessThan(new BigDecimal("1E-30"));
        assertThat(FinancialMath.root(new BigDecimal("0.25"), 2).subtract(new BigDecimal("0.5")).abs())
                .isLessThan(new BigDecimal("1E-30"));
    }

    @Test
    void rejectsRootsOfNonPositiveNumbers() {
        assertThatThrownBy(() -> FinancialMath.root(BigDecimal.ZERO, 12)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void growthCompoundsTheRate() {
        assertThat(FinancialMath.growth(new BigDecimal("0.1"), 2)).isEqualByComparingTo("1.21");
        assertThat(FinancialMath.growth(new BigDecimal("0.1"), 0)).isEqualByComparingTo("1");
    }

    @Test
    void ceilToUnitRoundsUpToTheNextPeso() {
        assertThat(FinancialMath.ceilToUnit(new BigDecimal("83333.3333"))).isEqualByComparingTo("83334");
        assertThat(FinancialMath.ceilToUnit(new BigDecimal("83333"))).isEqualByComparingTo("83333");
    }

    @Test
    void rootsOfFractionsConverge() {
        // 0,000001^(1/12) = 10^(-1/2)
        assertThat(FinancialMath.root(new BigDecimal("0.000001"), 12)
                .subtract(new BigDecimal("0.316227766016837933199889354443")).abs())
                .isLessThan(new BigDecimal("1E-28"));
    }

    @Test
    void theFirstRootIsTheNumberItself() {
        assertThat(FinancialMath.root(new BigDecimal("7.5"), 1)).isEqualByComparingTo("7.5");
        assertThatThrownBy(() -> FinancialMath.root(BigDecimal.TEN, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void growthRejectsNegativePeriods() {
        assertThatThrownBy(() -> FinancialMath.growth(new BigDecimal("0.1"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** H-03: 10^30 elevado a 1/12 es 10^2,5 = 316,2277… */
    @Test
    void rootsOfLargeNumbersConverge() {
        assertThat(FinancialMath.root(new BigDecimal("1E30"), 12)
                .subtract(new BigDecimal("316.227766016837933199889354443")).abs())
                .isLessThan(new BigDecimal("1E-25"));
    }

    @Test
    void rootsConvergeForManyMagnitudesAndIndexes() {
        for (String number : new String[] {"1.0000001", "1.12", "9.99", "11", "1234567.89", "1E18", "1E60", "0.5",
            "1E-20"}) {
            for (int index : new int[] {2, 3, 12, 52, 365}) {
                BigDecimal x = new BigDecimal(number);
                BigDecimal root = FinancialMath.root(x, index);

                BigDecimal relativeError = root.pow(index, FinancialMath.CONTEXT).subtract(x).abs()
                        .divide(x, FinancialMath.CONTEXT);
                assertThat(relativeError).as("raíz %s de %s", index, number).isLessThan(new BigDecimal("1E-28"));
            }
        }
    }
}
