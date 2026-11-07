package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.FinanceFakes.CLOCK;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.finance.application.FinanceFakes.InMemoryAccountRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryCategoryRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryTransactionRepository;
import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.dto.PeriodSummary;
import com.personaltracker.finance.application.dto.PeriodSummary.AccountTotals;
import com.personaltracker.finance.application.dto.PeriodSummary.CategoryTotal;
import com.personaltracker.finance.application.dto.PeriodSummary.Totals;
import com.personaltracker.finance.application.dto.PeriodSummary.TrendPoint;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.dto.TransactionDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.AdjustmentDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.identity.api.UserTimeZones;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El reloj de prueba marca el 6 de noviembre de 2026 a las 10:00 en Bogotá. */
class GetPeriodSummaryTests {

    private static final UserTimeZones BOGOTA = user -> ZoneId.of("America/Bogota");

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final GetPeriodSummary summary = new GetPeriodSummary(transactions, categories, BOGOTA, CLOCK);
    private final UserId owner = UserId.newId();
    private AccountId bank;
    private AccountId card;
    private AccountId other;
    private CategoryId food;
    private CategoryId groceries;
    private CategoryId transport;
    private CategoryId salary;

    @BeforeEach
    void setUp() {
        new SetUpFinance(accounts, categories, CLOCK).execute(new SetUpFinanceCommand(owner, false));
        other = accounts.findOtherAccount(owner).orElseThrow().id();
        CreateAccount createAccount = new CreateAccount(accounts, CLOCK);
        bank = createAccount.execute(new CreateAccountCommand(owner, new AccountDetails.Deposit(AccountKind.SAVINGS,
                "Ahorros", "Bancolombia", null, cop("0")))).id();
        card = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Visa", "Bancolombia", "5403", cop("5000000"), cop("0")))).id();
        CreateCategory createCategory = new CreateCategory(categories, CLOCK);
        food = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Alimentación", null)).id();
        groceries = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Mercado", food)).id();
        transport = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Transporte", null)).id();
        salary = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.INCOME, "Salario", null)).id();

        // Noviembre, hasta hoy (6).
        record(new IncomeDraft(bank, salary, cop("4000000"), on("2026-11-02")));
        record(new ExpenseDraft(card, groceries, cop("200000"), on("2026-11-03")));
        record(new ExpenseDraft(bank, food, cop("50000"), on("2026-11-05")));
        record(new ExpenseDraft(other, transport, cop("30000"), on("2026-11-06")));
        record(new ExpenseDraft(card, null, cop("10000"), on("2026-11-06")));
        // Neutros: no cuentan.
        record(new TransferDraft(bank, card, cop("100000"), on("2026-11-04")));
        record(new AdjustmentDraft(bank, AdjustmentDirection.INCREASE, cop("5000"), on("2026-11-04")));
        // Fecha futura dentro del mes: fuera del rango "hasta hoy".
        record(new ExpenseDraft(card, null, cop("999000"), on("2026-11-20")));
        // Octubre: del 1 al 6 entra en la comparación; el resto solo en la tendencia.
        record(new IncomeDraft(bank, salary, cop("3000000"), on("2026-10-01")));
        record(new ExpenseDraft(bank, food, cop("70000"), on("2026-10-03")));
        record(new ExpenseDraft(card, food, cop("500000"), on("2026-10-20")));
        // Junio es el primer punto de la tendencia; mayo queda fuera.
        record(new ExpenseDraft(card, null, cop("11000"), on("2026-06-15")));
        record(new ExpenseDraft(card, null, cop("1"), on("2026-05-31")));
    }

    @Test
    void comparesTheMonthInProgressWithTheSameDaysOfThePreviousMonth() {
        PeriodSummary result = summary.execute(owner, PeriodKind.MONTH, null);

        assertThat(result.period()).isEqualTo(range("2026-11-01", "2026-11-30"));
        assertThat(result.comparison().current()).isEqualTo(range("2026-11-01", "2026-11-06"));
        assertThat(result.comparison().previous()).isEqualTo(range("2026-10-01", "2026-10-06"));
        assertThat(result.current()).isEqualTo(Totals.of(cop("4000000"), cop("290000")));
        assertThat(result.current().net()).isEqualTo(cop("3710000"));
        assertThat(result.previous()).isEqualTo(Totals.of(cop("3000000"), cop("70000")));
    }

    @Test
    void breaksDownByAccountIncludingTheOtherAccount() {
        PeriodSummary result = summary.execute(owner, PeriodKind.MONTH, null);

        assertThat(result.byAccount()).containsExactly(
                new AccountTotals(bank, cop("4000000"), cop("50000")),
                new AccountTotals(card, cop("0"), cop("210000")),
                new AccountTotals(other, cop("0"), cop("30000")));
    }

    @Test
    void groupsExpensesByTopLevelCategory() {
        PeriodSummary result = summary.execute(owner, PeriodKind.MONTH, null);

        assertThat(result.expensesByCategory()).containsExactly(
                new CategoryTotal(food, cop("250000")),
                new CategoryTotal(transport, cop("30000")),
                new CategoryTotal(null, cop("10000")));
    }

    @Test
    void trendCoversTheLastSixMonthsEndingToday() {
        PeriodSummary result = summary.execute(owner, PeriodKind.MONTH, null);

        assertThat(result.trend()).hasSize(GetPeriodSummary.TREND_PERIODS);
        assertThat(result.trend()).extracting(TrendPoint::range).containsExactly(
                range("2026-06-01", "2026-06-30"),
                range("2026-07-01", "2026-07-31"),
                range("2026-08-01", "2026-08-31"),
                range("2026-09-01", "2026-09-30"),
                range("2026-10-01", "2026-10-31"),
                range("2026-11-01", "2026-11-06"));
        assertThat(result.trend().getFirst().expense()).isEqualTo(cop("11000"));
        assertThat(result.trend().get(4)).isEqualTo(
                new TrendPoint(range("2026-10-01", "2026-10-31"), cop("3000000"), cop("570000")));
        assertThat(result.trend().getLast().expense()).isEqualTo(cop("290000"));
    }

    @Test
    void aClosedMonthIsComparedComplete() {
        PeriodSummary result = summary.execute(owner, PeriodKind.MONTH, LocalDate.of(2026, 10, 15));

        assertThat(result.comparison().current()).isEqualTo(range("2026-10-01", "2026-10-31"));
        assertThat(result.comparison().previous()).isEqualTo(range("2026-09-01", "2026-09-30"));
        assertThat(result.current()).isEqualTo(Totals.of(cop("3000000"), cop("570000")));
        assertThat(result.previous()).isEqualTo(Totals.of(cop("0"), cop("0")));
    }

    @Test
    void weekAndDayUseTheirOwnRanges() {
        PeriodSummary week = summary.execute(owner, PeriodKind.WEEK, null);
        PeriodSummary day = summary.execute(owner, PeriodKind.DAY, null);

        assertThat(week.comparison().current()).isEqualTo(range("2026-11-02", "2026-11-06"));
        assertThat(week.current()).isEqualTo(Totals.of(cop("4000000"), cop("290000")));
        assertThat(day.comparison().current()).isEqualTo(range("2026-11-06", "2026-11-06"));
        assertThat(day.current()).isEqualTo(Totals.of(cop("0"), cop("40000")));
        assertThat(day.previous()).isEqualTo(Totals.of(cop("0"), cop("50000")));
    }

    @Test
    void todayDependsOnTheUserTimeZone() {
        // 03:00 UTC del 1 de noviembre: en Bogotá todavía es 31 de octubre.
        Clock earlyUtc = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneOffset.UTC);

        PeriodSummary inBogota = new GetPeriodSummary(transactions, categories, BOGOTA, earlyUtc)
                .execute(owner, PeriodKind.MONTH, null);
        PeriodSummary inMadrid = new GetPeriodSummary(transactions, categories, user -> ZoneId.of("Europe/Madrid"),
                earlyUtc).execute(owner, PeriodKind.MONTH, null);

        assertThat(inBogota.period()).isEqualTo(range("2026-10-01", "2026-10-31"));
        assertThat(inMadrid.period()).isEqualTo(range("2026-11-01", "2026-11-30"));
    }

    @Test
    void otherUsersSeeNothing() {
        PeriodSummary result = summary.execute(UserId.newId(), PeriodKind.MONTH, null);

        assertThat(result.current()).isEqualTo(Totals.of(cop("0"), cop("0")));
        assertThat(result.byAccount()).isEmpty();
        assertThat(result.expensesByCategory()).isEmpty();
    }

    private void record(TransactionDraft draft) {
        new RecordTransaction(transactions, accounts, categories, CLOCK).execute(new RecordTransactionCommand(owner, draft));
    }

    private static TransactionDetails on(String date) {
        return new TransactionDetails(LocalDate.parse(date), null, null, null);
    }

    private static DateRange range(String start, String end) {
        return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
    }
}
