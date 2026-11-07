package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.DailyTotal;
import com.personaltracker.finance.application.dto.PeriodSummary;
import com.personaltracker.finance.application.dto.PeriodSummary.AccountTotals;
import com.personaltracker.finance.application.dto.PeriodSummary.CategoryTotal;
import com.personaltracker.finance.application.dto.PeriodSummary.Totals;
import com.personaltracker.finance.application.dto.PeriodSummary.TrendPoint;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.Period;
import com.personaltracker.finance.domain.model.PeriodComparison;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.finance.domain.model.ReportEffect;
import com.personaltracker.finance.domain.model.SupportedCurrencies;
import com.personaltracker.identity.api.UserTimeZones;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumen de ingresos y gastos de un periodo, comparado con el anterior (ADR-0013). "Hoy" se calcula
 * en la zona horaria del perfil: decide si el periodo está en curso y es el periodo por defecto.
 * Las transferencias y los ajustes no cuentan.
 */
@Service
public class GetPeriodSummary {

    /** Periodos de la tendencia, incluido el actual. */
    public static final int TREND_PERIODS = 6;

    private final TransactionTotals totals;
    private final CategoryRepository categories;
    private final UserTimeZones timeZones;
    private final Clock clock;

    public GetPeriodSummary(
            TransactionTotals totals, CategoryRepository categories, UserTimeZones timeZones, Clock clock) {
        this.totals = totals;
        this.categories = categories;
        this.timeZones = timeZones;
        this.clock = clock;
    }

    /** {@code date} elige el periodo que la contiene; si es nula, se usa hoy. */
    @Transactional(readOnly = true)
    public PeriodSummary execute(UserId owner, PeriodKind kind, LocalDate date) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), timeZones.of(owner));
        Period period = Period.containing(kind, date == null ? today : date);
        PeriodComparison comparison = period.comparedAt(today);
        List<DateRange> trendRanges = trendRanges(period, comparison);

        List<DailyTotal> daily = totals.dailyTotals(
                owner, new DateRange(trendRanges.getFirst().start(), comparison.current().end()));
        List<DailyTotal> current = within(daily, comparison.current());

        return new PeriodSummary(
                kind,
                period.range(),
                comparison,
                totalsOf(current),
                totalsOf(within(daily, comparison.previous())),
                byAccount(current),
                expensesByCategory(current, owner),
                trendRanges.stream()
                        .map(range -> {
                            Totals rangeTotals = totalsOf(within(daily, range));
                            return new TrendPoint(range, rangeTotals.income(), rangeTotals.expense());
                        })
                        .toList());
    }

    /** Del periodo más antiguo al actual; el actual usa el mismo rango que sus totales. */
    private static List<DateRange> trendRanges(Period period, PeriodComparison comparison) {
        List<DateRange> ranges = new ArrayList<>();
        ranges.add(comparison.current());
        Period earlier = period;
        for (int i = 1; i < TREND_PERIODS; i++) {
            earlier = earlier.previous();
            ranges.addFirst(earlier.range());
        }
        return ranges;
    }

    private static List<DailyTotal> within(List<DailyTotal> daily, DateRange range) {
        return daily.stream().filter(total -> range.contains(total.date())).toList();
    }

    private static Totals totalsOf(List<DailyTotal> daily) {
        return Totals.of(sum(daily, ReportEffect.INCOME), sum(daily, ReportEffect.EXPENSE));
    }

    private static List<AccountTotals> byAccount(List<DailyTotal> current) {
        Map<AccountId, List<DailyTotal>> grouped = current.stream()
                .collect(Collectors.groupingBy(DailyTotal::account, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> new AccountTotals(entry.getKey(), sum(entry.getValue(), ReportEffect.INCOME),
                        sum(entry.getValue(), ReportEffect.EXPENSE)))
                .sorted(Comparator.comparing((AccountTotals totals) -> totals.income().plus(totals.expense()).amount())
                        .reversed())
                .toList();
    }

    /** Agrupa por la categoría principal; los gastos sin categoría van con {@code category} nula. */
    private List<CategoryTotal> expensesByCategory(List<DailyTotal> current, UserId owner) {
        Map<CategoryId, Category> byId = categories.findAllByOwner(owner).stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));
        Map<CategoryId, Money> grouped = new LinkedHashMap<>();
        for (DailyTotal total : current) {
            if (total.effect() == ReportEffect.EXPENSE) {
                grouped.merge(topLevel(total.category(), byId), total.total(), Money::plus);
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> new CategoryTotal(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing((CategoryTotal total) -> total.total().amount()).reversed())
                .toList();
    }

    private static CategoryId topLevel(CategoryId id, Map<CategoryId, Category> byId) {
        Category category = id == null ? null : byId.get(id);
        return category == null || category.isTopLevel() ? id : category.parentId();
    }

    private static Money sum(List<DailyTotal> daily, ReportEffect effect) {
        return daily.stream()
                .filter(total -> total.effect() == effect)
                .map(DailyTotal::total)
                .reduce(Money.zero(SupportedCurrencies.COP), Money::plus);
    }
}
