package com.personaltracker.shared.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.shared.domain.finance.Amortization.Row;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cuota fija y tabla de amortización (ADR-0018), contra valores calculados aparte con 50 dígitos. */
class AmortizationTests {

    private static BigDecimal monthly(String eaPercent) {
        return AnnualRate.ofPercent(new BigDecimal(eaPercent)).monthly();
    }

    private static BigDecimal installment(String principal, String eaPercent, int periods) {
        return FinancialMath.ceilToUnit(
                Amortization.fixedInstallment(new BigDecimal(principal), monthly(eaPercent), periods));
    }

    private static List<Row> schedule(String principal, String eaPercent, int periods) {
        return Amortization.schedule(new BigDecimal(principal), monthly(eaPercent), periods,
                installment(principal, eaPercent, periods));
    }

    private static BigDecimal sum(List<Row> rows, java.util.function.Function<Row, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void theFixedInstallmentMatchesTheClosedFormula() {
        assertThat(Amortization.fixedInstallment(new BigDecimal("60000000"), monthly("18"), 48)
                .setScale(4, RoundingMode.HALF_EVEN)).isEqualByComparingTo("1720955.5463");
        assertThat(Amortization.fixedInstallment(new BigDecimal("200000000"), monthly("12.5"), 240)
                .setScale(8, RoundingMode.HALF_EVEN)).isEqualByComparingTo("2179389.41770496");
        assertThat(Amortization.fixedInstallment(new BigDecimal("35000000"), monthly("26.49"), 60)
                .setScale(8, RoundingMode.HALF_EVEN)).isEqualByComparingTo("1001419.51808679");
    }

    @Test
    void withoutInterestTheInstallmentIsTheAmountDividedByThePeriods() {
        assertThat(Amortization.fixedInstallment(new BigDecimal("1200"), BigDecimal.ZERO, 12))
                .isEqualByComparingTo("100");
    }

    @Test
    void theScheduleOfTheExampleInTheDesignMatches() {
        List<Row> rows = schedule("60000000", "18", 48);

        assertThat(rows).hasSize(48);
        assertThat(rows.get(0)).isEqualTo(new Row(1, new BigDecimal("1720956.00"), new BigDecimal("833305.82"),
                new BigDecimal("887650.18"), new BigDecimal("59112349.82")));
        assertThat(rows.get(1).interest()).isEqualByComparingTo("820977.75");
        assertThat(rows.get(47)).isEqualTo(new Row(48, new BigDecimal("1720925.34"), new BigDecimal("23573.55"),
                new BigDecimal("1697351.79"), new BigDecimal("0.00")));
        assertThat(sum(rows, Row::interest)).isEqualByComparingTo("22605857.34");
    }

    @Test
    void aLongMortgageMatchesTheReferenceValues() {
        List<Row> rows = schedule("200000000", "12.5", 240);

        assertThat(rows.get(0)).isEqualTo(new Row(1, new BigDecimal("2179390.00"), new BigDecimal("1972716.11"),
                new BigDecimal("206673.89"), new BigDecimal("199793326.11")));
        assertThat(rows.get(239)).isEqualTo(new Row(240, new BigDecimal("2178826.23"), new BigDecimal("21281.12"),
                new BigDecimal("2157545.11"), new BigDecimal("0.00")));
        assertThat(sum(rows, Row::interest)).isEqualByComparingTo("323053036.23");
        assertThat(sum(rows, Row::payment)).isEqualByComparingTo("523053036.23");
    }

    @Test
    void withoutInterestTheLastInstallmentTakesTheRemainder() {
        List<Row> rows = Amortization.schedule(new BigDecimal("1000"), BigDecimal.ZERO, 3, new BigDecimal("334"));

        assertThat(rows).extracting(Row::payment)
                .containsExactly(new BigDecimal("334.00"), new BigDecimal("334.00"), new BigDecimal("332.00"));
        assertThat(rows).allSatisfy(row -> assertThat(row.interest()).isEqualByComparingTo("0"));
        assertThat(rows.get(2).balance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{0} al {1} % EA en {2} cuotas")
    @CsvSource({
        "60000000,   18,     48",
        "200000000,  12.5,   240",
        "35000000,   26.49,  60",
        "1500000,    0,      12",
        "999999.99,  33.33,  7",
        "480000000,  9.8,    360",
        "5000000,    1000,   24",
        "80000000,   2.5,    600",
        "1,          18,     1",
        "99999999999, 15,    120",
    })
    void everyScheduleAddsUp(String principal, String eaPercent, int periods) {
        BigDecimal installment = installment(principal, eaPercent, periods);
        List<Row> rows = schedule(principal, eaPercent, periods);

        assertThat(rows).hasSize(periods);
        assertThat(sum(rows, Row::principal)).as("el capital pagado es el monto financiado")
                .isEqualByComparingTo(principal);
        assertThat(rows.get(rows.size() - 1).balance()).isEqualByComparingTo("0");
        BigDecimal previousBalance = new BigDecimal(principal);
        for (Row row : rows) {
            assertThat(row.payment()).isEqualByComparingTo(row.interest().add(row.principal()));
            assertThat(row.balance()).isEqualByComparingTo(previousBalance.subtract(row.principal()));
            assertThat(row.principal().signum()).isGreaterThanOrEqualTo(0);
            assertThat(row.interest().scale()).isEqualTo(2);
            previousBalance = row.balance();
        }
        assertThat(rows.subList(0, rows.size() - 1))
                .allSatisfy(row -> assertThat(row.payment()).isEqualByComparingTo(installment));
        assertThat(rows.get(rows.size() - 1).payment()).as("la última cuota ajusta el residuo y no supera a las demás")
                .isLessThanOrEqualTo(installment);
    }

    /** Una cuota de $234 durante 50 años: el redondeo al peso salda la deuda tres cuotas antes. */
    @Test
    void roundingATinyInstallmentUpPaysOffAFewPeriodsEarly() {
        List<Row> rows = schedule("80000", "2.5", 600);

        assertThat(rows).hasSize(597);
        assertThat(sum(rows, Row::principal)).isEqualByComparingTo("80000");
        assertThat(rows.get(596).balance()).isEqualByComparingTo("0");
        assertThat(rows).allSatisfy(row -> assertThat(row.principal().signum()).isGreaterThanOrEqualTo(0));
    }

    /** Con montos muy pequeños, redondear la cuota hacia arriba salda la deuda antes de tiempo. */
    @Test
    void anInstallmentThatPaysOffEarlyEndsTheScheduleEarly() {
        List<Row> rows = Amortization.schedule(new BigDecimal("10"), BigDecimal.ZERO, 600, BigDecimal.ONE);

        assertThat(rows).hasSize(10);
        assertThat(sum(rows, Row::principal)).isEqualByComparingTo("10");
        assertThat(rows.get(9).balance()).isEqualByComparingTo("0");
    }

    @Test
    void anInstallmentThatExactlyPaysOffEndsWithAZeroBalance() {
        List<Row> rows = Amortization.schedule(new BigDecimal("300"), BigDecimal.ZERO, 3, new BigDecimal("100"));

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(Row::balance)
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("100.00"), new BigDecimal("0.00"));
    }

    /** Una cuota que no cubre el interés no abona capital: la deuda no crece y se paga al final. */
    @Test
    void anInstallmentBelowTheInterestPaysNoCapitalUntilTheLastOne() {
        List<Row> rows =
                Amortization.schedule(new BigDecimal("1000000"), new BigDecimal("0.02"), 3, new BigDecimal("5000"));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).isEqualTo(new Row(1, new BigDecimal("20000.00"), new BigDecimal("20000.00"),
                new BigDecimal("0.00"), new BigDecimal("1000000.00")));
        assertThat(rows.get(2).principal()).isEqualByComparingTo("1000000");
        assertThat(rows.get(2).balance()).isEqualByComparingTo("0");
    }

    @Test
    void interestIsRoundedHalfEvenToTheCent() {
        // 1.000,25 × 1 % = 10,0025 → 10,00; 1.000,50 × 1 % = 10,005 → 10,00 (par); 1.001,50 × 1 % = 10,015 → 10,02.
        BigDecimal rate = new BigDecimal("0.01");
        assertThat(Amortization.schedule(new BigDecimal("1000.25"), rate, 1, BigDecimal.TEN).get(0).interest())
                .isEqualByComparingTo("10.00");
        assertThat(Amortization.schedule(new BigDecimal("1000.50"), rate, 1, BigDecimal.TEN).get(0).interest())
                .isEqualByComparingTo("10.00");
        assertThat(Amortization.schedule(new BigDecimal("1001.50"), rate, 1, BigDecimal.TEN).get(0).interest())
                .isEqualByComparingTo("10.02");
    }

    @Test
    void rejectsTermsThatMakeNoSense() {
        BigDecimal rate = new BigDecimal("0.01");
        assertThatThrownBy(() -> Amortization.fixedInstallment(BigDecimal.ZERO, rate, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amortization.fixedInstallment(BigDecimal.TEN, rate.negate(), 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amortization.fixedInstallment(BigDecimal.TEN, rate, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amortization.schedule(BigDecimal.TEN, rate, 12, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amortization.schedule(BigDecimal.TEN, rate, 12, new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amortization.schedule(new BigDecimal("10.001"), rate, 12, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
