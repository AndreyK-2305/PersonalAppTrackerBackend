package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.PeriodSummary;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de ingresos y gastos de un periodo (ADR-0013). Los montos son cadenas decimales en
 * {@code currency}. {@code current} y el detalle por producto y categoría usan el rango actual, que en
 * un periodo en curso llega hasta hoy; {@code previous} es el rango equivalente del periodo anterior.
 * Las transferencias y los ajustes no cuentan.
 *
 * @param start primer día del periodo completo
 * @param end último día del periodo completo
 * @param expensesByCategory gastos por categoría principal, de mayor a menor; {@code categoryId} nulo
 *     agrupa los gastos sin categoría
 * @param trend los últimos 6 periodos del mismo tipo, del más antiguo al actual
 */
@Schema(requiredProperties = {"kind", "start", "end", "currency", "current", "previous", "byAccount",
        "expensesByCategory", "trend"})
record SummaryResponse(
        PeriodKind kind,
        LocalDate start,
        LocalDate end,
        String currency,
        Totals current,
        Totals previous,
        List<AccountTotals> byAccount,
        List<CategoryTotal> expensesByCategory,
        List<TrendPoint> trend) {

    static SummaryResponse from(PeriodSummary summary) {
        return new SummaryResponse(
                summary.kind(),
                summary.period().start(),
                summary.period().end(),
                summary.current().income().currency().getCurrencyCode(),
                Totals.of(summary.comparison().current(), summary.current()),
                Totals.of(summary.comparison().previous(), summary.previous()),
                summary.byAccount().stream()
                        .map(totals -> new AccountTotals(
                                totals.account().value(), format(totals.income()), format(totals.expense())))
                        .toList(),
                summary.expensesByCategory().stream()
                        .map(total -> new CategoryTotal(
                                total.category() == null ? null : total.category().value(), format(total.total())))
                        .toList(),
                summary.trend().stream()
                        .map(point -> new TrendPoint(point.range().start(), point.range().end(),
                                format(point.income()), format(point.expense())))
                        .toList());
    }

    private static String format(Money money) {
        return Amounts.format(money);
    }

    /** Totales de un rango; {@code net} es ingresos menos gastos. */
    @Schema(name = "SummaryTotals", requiredProperties = {"start", "end", "income", "expense", "net"})
    record Totals(LocalDate start, LocalDate end, String income, String expense, String net) {

        static Totals of(DateRange range, PeriodSummary.Totals totals) {
            return new Totals(range.start(), range.end(), format(totals.income()), format(totals.expense()),
                    format(totals.net()));
        }
    }

    @Schema(name = "SummaryAccountTotals", requiredProperties = {"accountId", "income", "expense"})
    record AccountTotals(UUID accountId, String income, String expense) {}

    @Schema(name = "SummaryCategoryTotal", requiredProperties = "total")
    record CategoryTotal(UUID categoryId, String total) {}

    @Schema(name = "SummaryTrendPoint", requiredProperties = {"start", "end", "income", "expense"})
    record TrendPoint(LocalDate start, LocalDate end, String income, String expense) {}
}
