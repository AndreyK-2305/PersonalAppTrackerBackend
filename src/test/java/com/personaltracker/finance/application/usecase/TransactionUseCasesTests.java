package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.FinanceFakes.CLOCK;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.FinanceFakes.InMemoryAccountRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryCategoryRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryTransactionRepository;
import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.dto.ListTransactionsQuery;
import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.TransactionDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.AdjustmentDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.dto.TransactionView;
import com.personaltracker.finance.application.dto.UpdateTransactionCommand;
import com.personaltracker.finance.domain.exception.AccountNotFoundException;
import com.personaltracker.finance.domain.exception.CategoryNotFoundException;
import com.personaltracker.finance.domain.exception.InvalidDateRangeException;
import com.personaltracker.finance.domain.exception.TransactionNotFoundException;
import com.personaltracker.finance.domain.exception.TransactionTypeChangeException;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionUseCasesTests {

    private static final LocalDate NOV_6 = LocalDate.of(2026, 11, 6);

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final RecordTransaction record = new RecordTransaction(transactions, accounts, categories, CLOCK);
    private final UpdateTransaction update = new UpdateTransaction(transactions, accounts, categories, CLOCK);
    private final DeleteTransaction delete = new DeleteTransaction(transactions, event -> {});
    private final ListTransactions list = new ListTransactions(transactions, accounts, categories);
    private final UserId owner = UserId.newId();
    private AccountView bank;
    private AccountView card;
    private CategoryView food;
    private CategoryView groceries;
    private CategoryView salary;

    @BeforeEach
    void setUp() {
        CreateAccount createAccount = new CreateAccount(accounts, CLOCK);
        bank = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.SAVINGS, "Ahorros",
                        "Bancolombia", "1234", cop("1000000"))));
        card = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Visa", "Bancolombia", "5403", cop("3000000"), cop("0"))));
        CreateCategory createCategory = new CreateCategory(categories, CLOCK);
        food = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Alimentación", null));
        groceries = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Mercado", food.id()));
        salary = createCategory.execute(new CreateCategoryCommand(owner, CategoryKind.INCOME, "Salario", null));
    }

    @Test
    void recordsEachTypeOfTransaction() {
        TransactionView income = recordDraft(new IncomeDraft(bank.id(), salary.id(), cop("4500000"), on(NOV_6)));
        TransactionView expense = recordDraft(new ExpenseDraft(card.id(), groceries.id(), cop("85000"),
                new TransactionDetails(NOV_6, "Mercado semanal", "Éxito", PaymentChannel.CARD)));
        TransactionView transfer = recordDraft(new TransferDraft(bank.id(), card.id(), cop("85000"), on(NOV_6)));
        TransactionView adjustment = recordDraft(
                new AdjustmentDraft(bank.id(), AdjustmentDirection.DECREASE, cop("4000"), on(NOV_6)));

        assertThat(income.type()).isEqualTo(TransactionType.INCOME);
        assertThat(income.destinationAccount()).isEqualTo(bank.id());
        assertThat(expense.sourceAccount()).isEqualTo(card.id());
        assertThat(expense.merchant()).isEqualTo("Éxito");
        assertThat(expense.channel()).isEqualTo(PaymentChannel.CARD);
        assertThat(transfer.category()).isNull();
        assertThat(adjustment.sourceAccount()).isEqualTo(bank.id());
        assertThat(adjustment.direction()).isEqualTo(AdjustmentDirection.DECREASE);
        assertThat(transactions.count()).isEqualTo(4);
    }

    @Test
    void anotherUsersAccountOrCategoryIsNotFound() {
        UserId intruder = UserId.newId();
        AccountView intruderCash = new CreateAccount(accounts, CLOCK)
                .execute(new CreateAccountCommand(intruder, new AccountDetails.Cash("Billetera", cop("0"))));

        assertThatThrownBy(() -> record.execute(new RecordTransactionCommand(intruder,
                new ExpenseDraft(bank.id(), null, cop("1"), on(NOV_6)))))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> record.execute(new RecordTransactionCommand(intruder,
                new ExpenseDraft(intruderCash.id(), food.id(), cop("1"), on(NOV_6)))))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void updatesATransactionOfTheSameType() {
        TransactionView expense = recordDraft(new ExpenseDraft(card.id(), null, cop("20000"), on(NOV_6)));

        TransactionView updated = update.execute(new UpdateTransactionCommand(owner, expense.id(),
                new ExpenseDraft(bank.id(), groceries.id(), cop("25000"), on(NOV_6.minusDays(1)))));

        assertThat(updated.sourceAccount()).isEqualTo(bank.id());
        assertThat(updated.category()).isEqualTo(groceries.id());
        assertThat(updated.amount()).isEqualTo(cop("25000"));
        assertThat(updated.date()).isEqualTo(NOV_6.minusDays(1));
    }

    @Test
    void rejectsChangingTheType() {
        TransactionView expense = recordDraft(new ExpenseDraft(card.id(), null, cop("20000"), on(NOV_6)));

        assertThatThrownBy(() -> update.execute(new UpdateTransactionCommand(owner, expense.id(),
                new TransferDraft(bank.id(), card.id(), cop("20000"), on(NOV_6)))))
                .isInstanceOf(TransactionTypeChangeException.class)
                .extracting("code").isEqualTo("finance.transaction.type_change");
    }

    @Test
    void deletesOwnTransactionsOnly() {
        TransactionView expense = recordDraft(new ExpenseDraft(card.id(), null, cop("20000"), on(NOV_6)));

        assertThatThrownBy(() -> delete.execute(UserId.newId(), expense.id()))
                .isInstanceOf(TransactionNotFoundException.class);

        delete.execute(owner, expense.id());

        assertThat(transactions.count()).isZero();
    }

    @Test
    void filtersByCategoryIncludingSubcategoriesAndByAccountOnEitherSide() {
        recordDraft(new ExpenseDraft(card.id(), food.id(), cop("10000"), on(NOV_6)));
        recordDraft(new ExpenseDraft(bank.id(), groceries.id(), cop("20000"), on(NOV_6)));
        recordDraft(new IncomeDraft(bank.id(), salary.id(), cop("30000"), on(NOV_6)));
        recordDraft(new TransferDraft(bank.id(), card.id(), cop("40000"), on(NOV_6)));

        assertThat(amounts(query().category(food.id()))).containsExactlyInAnyOrder("10000.00", "20000.00");
        assertThat(amounts(query().category(groceries.id()))).containsExactly("20000.00");
        assertThat(amounts(query().account(card.id()))).containsExactlyInAnyOrder("10000.00", "40000.00");
        assertThat(amounts(query().type(TransactionType.INCOME))).containsExactly("30000.00");
    }

    @Test
    void filtersByDatesAndTextAndSorts() {
        recordDraft(new ExpenseDraft(card.id(), null, cop("10000"), new TransactionDetails(NOV_6.minusDays(10), "Cine", null, null)));
        recordDraft(new ExpenseDraft(card.id(), null, cop("30000"), new TransactionDetails(NOV_6, null, "Cinemark", null)));
        recordDraft(new ExpenseDraft(card.id(), null, cop("20000"), new TransactionDetails(NOV_6.minusDays(2), "Taxi", null, null)));

        assertThat(amounts(query().from(NOV_6.minusDays(5)).to(NOV_6))).containsExactly("30000.00", "20000.00");
        assertThat(amounts(query().text("  cine "))).containsExactly("30000.00", "10000.00");
        assertThat(amounts(query().sort(TransactionSort.AMOUNT_ASC))).containsExactly("10000.00", "20000.00", "30000.00");
    }

    @Test
    void paginates() {
        for (int day = 1; day <= 5; day++) {
            recordDraft(new ExpenseDraft(card.id(), null, cop(day + "000"), on(NOV_6.minusDays(day))));
        }

        PageResult<TransactionView> page = list.execute(query().page(new PageQuery(1, 2)).build());

        assertThat(page.items()).extracting(view -> view.amount().amount().toPlainString())
                .containsExactly("3000.00", "4000.00");
        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsAnInvertedDateRangeAndForeignFilters() {
        assertThatThrownBy(() -> list.execute(query().from(NOV_6).to(NOV_6.minusDays(1)).build()))
                .isInstanceOf(InvalidDateRangeException.class);

        UserId intruder = UserId.newId();
        assertThatThrownBy(() -> list.execute(new QueryBuilder(intruder).account(bank.id()).build()))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> list.execute(new QueryBuilder(intruder).category(food.id()).build()))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    private TransactionView recordDraft(TransactionDraft draft) {
        return record.execute(new RecordTransactionCommand(owner, draft));
    }

    private QueryBuilder query() {
        return new QueryBuilder(owner);
    }

    private List<String> amounts(QueryBuilder query) {
        return list.execute(query.build()).items().stream()
                .map(view -> view.amount().amount().toPlainString())
                .toList();
    }

    private static TransactionDetails on(LocalDate date) {
        return new TransactionDetails(date, null, null, null);
    }

    /** Arma consultas legibles en las pruebas; por defecto, sin filtros y la primera página de 20. */
    private static final class QueryBuilder {

        private final UserId owner;
        private LocalDate from;
        private LocalDate to;
        private AccountId account;
        private CategoryId category;
        private TransactionType type;
        private String text;
        private TransactionSort sort;
        private PageQuery page = new PageQuery(0, 20);

        QueryBuilder(UserId owner) {
            this.owner = owner;
        }

        QueryBuilder from(LocalDate value) {
            from = value;
            return this;
        }

        QueryBuilder to(LocalDate value) {
            to = value;
            return this;
        }

        QueryBuilder account(AccountId value) {
            account = value;
            return this;
        }

        QueryBuilder category(CategoryId value) {
            category = value;
            return this;
        }

        QueryBuilder type(TransactionType value) {
            type = value;
            return this;
        }

        QueryBuilder text(String value) {
            text = value;
            return this;
        }

        QueryBuilder sort(TransactionSort value) {
            sort = value;
            return this;
        }

        QueryBuilder page(PageQuery value) {
            page = value;
            return this;
        }

        ListTransactionsQuery build() {
            return new ListTransactionsQuery(owner, from, to, account, category, type, text, sort, page);
        }
    }
}
