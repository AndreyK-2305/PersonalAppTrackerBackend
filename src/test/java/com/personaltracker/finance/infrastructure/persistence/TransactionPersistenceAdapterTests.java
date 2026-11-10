package com.personaltracker.finance.infrastructure.persistence;

import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.TransactionSearch;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.FinanceTestData;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class TransactionPersistenceAdapterTests {

    private static final LocalDate NOV_6 = LocalDate.of(2026, 11, 6);
    private static final PageQuery FIRST_PAGE = new PageQuery(0, 20);

    @Autowired
    TransactionRepository transactions;

    @Autowired
    AccountRepository accounts;

    @Autowired
    CategoryRepository categories;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    private UserId owner;
    private DepositAccount bank;
    private CreditCardAccount card;
    private CashAccount cash;
    private Category food;
    private Category groceries;
    private Category salary;

    @BeforeEach
    void setUp() {
        owner = newUser();
        bank = FinanceTestData.savings(owner, "Ahorros");
        card = FinanceTestData.creditCard(owner);
        cash = FinanceTestData.cash(owner);
        List.of(bank, card, cash).forEach(accounts::save);
        food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        groceries = Category.createChild(CategoryId.newId(), food, "Mercado", NOW);
        salary = FinanceTestData.category(owner, CategoryKind.INCOME, "Salario");
        List.of(food, groceries, salary).forEach(categories::save);
    }

    @Test
    void savesAndRestoresEveryTypeOfTransaction() {
        List<Transaction> saved = List.of(
                Income.record(TransactionId.newId(), owner, bank, salary, cop("4500000"),
                        new TransactionDetails(NOV_6, "Nómina", "Empresa S.A.S.", PaymentChannel.BANK_TRANSFER), NOW),
                Expense.record(TransactionId.newId(), owner, card, groceries, cop("85000.50"),
                        new TransactionDetails(NOV_6, null, "Éxito", PaymentChannel.CARD), NOW),
                Expense.record(TransactionId.newId(), owner, cash, null, cop("2000"), on(NOV_6), NOW),
                Transfer.record(TransactionId.newId(), owner, bank, card, cop("300000"), on(NOV_6), NOW),
                Adjustment.record(TransactionId.newId(), owner, bank, AdjustmentDirection.INCREASE, cop("10"),
                        on(NOV_6), NOW),
                Adjustment.record(TransactionId.newId(), owner, cash, AdjustmentDirection.DECREASE, cop("20"),
                        on(NOV_6), NOW));

        saved.forEach(transactions::save);

        for (Transaction transaction : saved) {
            assertThat(transactions.findByIdAndOwner(transaction.id(), owner).orElseThrow())
                    .usingRecursiveComparison()
                    .isEqualTo(transaction);
        }
    }

    @Test
    void updatesAndDeletes() {
        Expense expense = Expense.record(TransactionId.newId(), owner, card, null, cop("1000"), on(NOV_6), NOW);
        transactions.save(expense);

        Expense loaded = (Expense) transactions.findByIdAndOwner(expense.id(), owner).orElseThrow();
        loaded.revise(bank, food, cop("1500"), on(NOV_6.minusDays(1)), NOW);
        transactions.save(loaded);

        assertThat(transactions.findByIdAndOwner(expense.id(), owner).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(loaded);

        transactions.delete(loaded);

        assertThat(transactions.findByIdAndOwner(expense.id(), owner)).isEmpty();
    }

    @Test
    void searchesWithEveryFilter() {
        // Se registran en segundos distintos para que el orden a igual fecha sea determinista.
        save(Expense.record(TransactionId.newId(), owner, card, food, cop("10000"),
                new TransactionDetails(NOV_6.minusDays(10), "Cine 100% gratis", null, null), NOW.plusSeconds(1)));
        save(Expense.record(TransactionId.newId(), owner, bank, groceries, cop("20000"),
                new TransactionDetails(NOV_6.minusDays(2), null, "Cinemark", null), NOW.plusSeconds(2)));
        save(Income.record(TransactionId.newId(), owner, bank, salary, cop("30000"), on(NOV_6), NOW.plusSeconds(3)));
        save(Transfer.record(TransactionId.newId(), owner, bank, card, cop("40000"), on(NOV_6), NOW.plusSeconds(4)));

        assertThat(amounts(new TransactionSearch(NOV_6.minusDays(5), NOV_6, null, null, null, null)))
                .containsExactly("40000.00", "30000.00", "20000.00");
        assertThat(amounts(new TransactionSearch(null, null, card.id(), null, null, null)))
                .containsExactly("40000.00", "10000.00");
        assertThat(amounts(new TransactionSearch(null, null, null, Set.of(food.id(), groceries.id()), null, null)))
                .containsExactly("20000.00", "10000.00");
        assertThat(amounts(new TransactionSearch(null, null, null, Set.of(), null, null))).isEmpty();
        assertThat(amounts(new TransactionSearch(null, null, null, null, TransactionType.INCOME, null)))
                .containsExactly("30000.00");
        assertThat(amounts(new TransactionSearch(null, null, null, null, null, "CINE")))
                .containsExactly("20000.00", "10000.00");
        assertThat(amounts(new TransactionSearch(null, null, null, null, null, "100%")))
                .containsExactly("10000.00");
        assertThat(amounts(new TransactionSearch(null, null, null, null, null, "_")))
                .isEmpty();
    }

    @Test
    void uncategorizedKeepsOnlyIncomesAndExpensesWithoutCategory() {
        save(Expense.record(TransactionId.newId(), owner, card, null, cop("10000"), on(NOV_6), NOW.plusSeconds(1)));
        save(Expense.record(TransactionId.newId(), owner, card, food, cop("20000"), on(NOV_6), NOW.plusSeconds(2)));
        save(Income.record(TransactionId.newId(), owner, bank, null, cop("30000"), on(NOV_6), NOW.plusSeconds(3)));
        save(Transfer.record(TransactionId.newId(), owner, bank, card, cop("40000"), on(NOV_6), NOW.plusSeconds(4)));

        assertThat(amounts(new TransactionSearch(null, null, null, null, null, null, true)))
                .containsExactly("30000.00", "10000.00");
    }

    @Test
    void sortsAndPaginatesStably() {
        for (int day = 1; day <= 5; day++) {
            save(Expense.record(TransactionId.newId(), owner, card, null, cop(day + "000"), on(NOV_6.minusDays(day)),
                    NOW.plusSeconds(day)));
        }
        // Mismo día y monto: gana el registrado más recientemente.
        Expense older = save(Expense.record(TransactionId.newId(), owner, cash, null, cop("7000"), on(NOV_6), NOW));
        Expense newer = save(Expense.record(TransactionId.newId(), owner, cash, null, cop("7000"), on(NOV_6),
                NOW.plusSeconds(60)));

        PageResult<Transaction> page = transactions.search(owner, all(), TransactionSort.DATE_DESC, new PageQuery(0, 3));
        assertThat(page.items()).extracting(Transaction::id)
                .startsWith(newer.id(), older.id());
        assertThat(page.totalItems()).isEqualTo(7);
        assertThat(page.totalPages()).isEqualTo(3);

        PageResult<Transaction> last = transactions.search(owner, all(), TransactionSort.AMOUNT_ASC, new PageQuery(2, 3));
        assertThat(last.items()).extracting(transaction -> transaction.amount().amount().toPlainString())
                .containsExactly("7000.00");
    }

    @Test
    void detectsTransactionsByAccountOrCategory() {
        save(Transfer.record(TransactionId.newId(), owner, bank, card, cop("1"), on(NOV_6), NOW));
        save(Expense.record(TransactionId.newId(), owner, cash, groceries, cop("1"), on(NOV_6), NOW));

        assertThat(transactions.existsByAccount(bank.id(), owner)).isTrue();
        assertThat(transactions.existsByAccount(card.id(), owner)).isTrue();
        assertThat(transactions.existsByAnyCategory(Set.of(groceries.id()), owner)).isTrue();
        assertThat(transactions.existsByAnyCategory(Set.of(food.id(), salary.id()), owner)).isFalse();
        assertThat(transactions.existsByAnyCategory(Set.of(), owner)).isFalse();
    }

    @Test
    void neverReturnsAnotherUsersTransactions() {
        Expense expense = save(Expense.record(TransactionId.newId(), owner, card, null, cop("1"), on(NOV_6), NOW));
        UserId intruder = newUser();

        assertThat(transactions.findByIdAndOwner(expense.id(), intruder)).isEmpty();
        assertThat(transactions.search(intruder, all(), TransactionSort.DATE_DESC, FIRST_PAGE).items()).isEmpty();
        assertThat(transactions.existsByAccount(card.id(), intruder)).isFalse();
    }

    @Test
    void theDatabaseRejectsLinksToAnotherUsersAccount() {
        UserId intruder = newUser();

        assertThatThrownBy(() -> insertExpense(intruder, card.id().value(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertExpense(owner, card.id().value(), null)).doesNotThrowAnyException();
    }

    @Test
    void theDatabaseEnforcesTheColumnsOfEachType() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into finance.transactions (id, user_id, type, amount, currency, transaction_date,
                    source_account_id, destination_account_id, created_at, updated_at, version)
                values (?, ?, 'TRANSFER', 1, 'COP', current_date, ?, ?, now(), now(), 0)
                """, UUID.randomUUID(), owner.value(), bank.id().value(), bank.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into finance.transactions (id, user_id, type, amount, currency, transaction_date,
                    destination_account_id, created_at, updated_at, version)
                values (?, ?, 'EXPENSE', 1, 'COP', current_date, ?, now(), now(), 0)
                """, UUID.randomUUID(), owner.value(), bank.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Un gasto insertado directamente en la tabla; la categoría puede ser nula. */
    private void insertExpense(UserId user, UUID source, UUID category) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                insert into finance.transactions (id, user_id, type, amount, currency, transaction_date,
                    source_account_id, category_id, created_at, updated_at, version)
                values (?, ?, 'EXPENSE', 1, 'COP', current_date, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), user.value(), source, category, now, now);
    }

    private <T extends Transaction> T save(T transaction) {
        transactions.save(transaction);
        return transaction;
    }

    private List<String> amounts(TransactionSearch search) {
        return transactions.search(owner, search, TransactionSort.DATE_DESC, FIRST_PAGE).items().stream()
                .map(transaction -> transaction.amount().amount().toPlainString())
                .toList();
    }

    private static TransactionSearch all() {
        return new TransactionSearch(null, null, null, null, null, null);
    }

    private static TransactionDetails on(LocalDate date) {
        return new TransactionDetails(date, null, null, null);
    }

    private UserId newUser() {
        return new TestAccounts(users, passwordHasher).create().id();
    }
}
