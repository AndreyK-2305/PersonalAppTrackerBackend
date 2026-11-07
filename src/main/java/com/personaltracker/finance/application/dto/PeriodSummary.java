package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.PeriodComparison;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.shared.domain.Money;
import java.util.List;

/**
 * Resumen de ingresos y gastos de un periodo (ADR-0013): base del dashboard y, en la Etapa 5, de los
 * reportes programados. Los totales del periodo, por producto y por categoría usan el rango
 * {@code comparison.current()}, que en un periodo en curso llega hasta hoy.
 *
 * @param period rango completo del periodo pedido
 * @param byAccount ingresos y gastos por producto, de mayor a menor movimiento
 * @param expensesByCategory gastos por categoría principal, de mayor a menor; {@code category} nula
 *     agrupa los gastos sin categoría
 * @param trend los últimos periodos del mismo tipo, del más antiguo al actual
 */
public record PeriodSummary(
        PeriodKind kind,
        DateRange period,
        PeriodComparison comparison,
        Totals current,
        Totals previous,
        List<AccountTotals> byAccount,
        List<CategoryTotal> expensesByCategory,
        List<TrendPoint> trend) {

    public PeriodSummary {
        byAccount = List.copyOf(byAccount);
        expensesByCategory = List.copyOf(expensesByCategory);
        trend = List.copyOf(trend);
    }

    /** {@code net} es ingresos menos gastos. */
    public record Totals(Money income, Money expense, Money net) {

        public static Totals of(Money income, Money expense) {
            return new Totals(income, expense, income.minus(expense));
        }
    }

    public record AccountTotals(AccountId account, Money income, Money expense) {}

    public record CategoryTotal(CategoryId category, Money total) {}

    public record TrendPoint(DateRange range, Money income, Money expense) {}
}
