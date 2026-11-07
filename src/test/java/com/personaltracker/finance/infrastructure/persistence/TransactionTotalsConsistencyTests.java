package com.personaltracker.finance.infrastructure.persistence;

import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.finance.application.FinanceFakes.InMemoryTransactionRepository;
import com.personaltracker.finance.application.dto.DailyTotal;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.FinanceTestData;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Las sumas de {@link TransactionTotalsAdapter} se calculan en SQL y deben coincidir con las reglas
 * del dominio ({@code reportEffect()} y {@code balanceEffects()}), que aplica el doble en memoria de
 * {@code FinanceFakes} (ADR-0013). Se comparan sobre un conjunto variado de movimientos generado con
 * una semilla fija.
 */
@IntegrationTest
class TransactionTotalsConsistencyTests {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Autowired
    TransactionRepository transactions;

    @Autowired
    TransactionTotals totals;

    @Autowired
    AccountRepository accounts;

    @Autowired
    CategoryRepository categories;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private final InMemoryTransactionRepository reference = new InMemoryTransactionRepository();
    private UserId owner;

    @BeforeEach
    void recordAVariedSetOfTransactions() {
        owner = new TestAccounts(users, passwordHasher).create().id();
        Account bank = FinanceTestData.savings(owner, "Ahorros");
        Account card = FinanceTestData.creditCard(owner);
        Account cash = FinanceTestData.cash(owner);
        Account other = FinanceTestData.other(owner);
        List<Account> withBalance = List.of(bank, card, cash);
        List.of(bank, card, cash, other).forEach(accounts::save);

        Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        Category groceries = Category.createChild(CategoryId.newId(), food, "Mercado", NOW);
        Category salary = FinanceTestData.category(owner, CategoryKind.INCOME, "Salario");
        List.of(food, groceries, salary).forEach(categories::save);
        List<Category> expenseCategories = new ArrayList<>(List.of(food, groceries));
        expenseCategories.add(null);

        Random random = new Random(20261106);
        for (int i = 0; i < 80; i++) {
            LocalDate date = START.plusDays(random.nextInt(75));
            Money amount = cop(String.valueOf(1 + random.nextInt(500_000)) + "." + random.nextInt(10) + "5");
            TransactionDetails details = new TransactionDetails(date, null, null, null);
            Account first = withBalance.get(random.nextInt(withBalance.size()));
            Transaction transaction = switch (i % 5) {
                case 0 -> Income.record(TransactionId.newId(), owner, random.nextBoolean() ? first : other,
                        random.nextBoolean() ? salary : null, amount, details, NOW);
                case 1, 2 -> Expense.record(TransactionId.newId(), owner, random.nextInt(4) == 0 ? other : first,
                        expenseCategories.get(random.nextInt(expenseCategories.size())), amount, details, NOW);
                case 3 -> Transfer.record(TransactionId.newId(), owner, first,
                        withBalance.get((withBalance.indexOf(first) + 1) % withBalance.size()), amount, details, NOW);
                default -> Adjustment.record(TransactionId.newId(), owner, first,
                        random.nextBoolean() ? AdjustmentDirection.INCREASE : AdjustmentDirection.DECREASE, amount,
                        details, NOW);
            };
            transactions.save(transaction);
            reference.save(transaction);
        }
    }

    @Test
    void dailyTotalsMatchTheDomainRules() {
        DateRange everything = new DateRange(START, START.plusDays(75));
        DateRange slice = new DateRange(START.plusDays(10), START.plusDays(40));

        List<DailyTotal> all = totals.dailyTotals(owner, everything);

        assertThat(all).isNotEmpty().containsExactlyInAnyOrderElementsOf(reference.dailyTotals(owner, everything));
        assertThat(totals.dailyTotals(owner, slice))
                .containsExactlyInAnyOrderElementsOf(reference.dailyTotals(owner, slice));
    }

    @Test
    void balanceChangesMatchTheDomainRules() {
        assertThat(totals.balanceChanges(owner)).isNotEmpty().isEqualTo(reference.balanceChanges(owner));
    }

    @Test
    void anotherUserHasNoTotals() {
        UserId intruder = new TestAccounts(users, passwordHasher).create().id();

        assertThat(totals.dailyTotals(intruder, new DateRange(START, START.plusDays(75)))).isEmpty();
        assertThat(totals.balanceChanges(intruder)).isEmpty();
    }
}
