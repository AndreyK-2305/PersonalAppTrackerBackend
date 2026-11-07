package com.personaltracker.finance;

import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountList;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.PeriodSummary;
import com.personaltracker.finance.application.dto.PeriodSummary.CategoryTotal;
import com.personaltracker.finance.application.dto.PeriodSummary.Totals;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.dto.TransactionDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.IncomeDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.application.usecase.CreateAccount;
import com.personaltracker.finance.application.usecase.DeleteAccount;
import com.personaltracker.finance.application.usecase.DeleteCategory;
import com.personaltracker.finance.application.usecase.GetFinanceStatus;
import com.personaltracker.finance.application.usecase.GetPeriodSummary;
import com.personaltracker.finance.application.usecase.ListAccounts;
import com.personaltracker.finance.application.usecase.ListCategories;
import com.personaltracker.finance.application.usecase.RecordTransaction;
import com.personaltracker.finance.application.usecase.SetUpFinance;
import com.personaltracker.finance.domain.exception.AccountInUseException;
import com.personaltracker.finance.domain.exception.CategoryInUseException;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Casos de uso de finance con los adaptadores reales y PostgreSQL: verifica el cableado, las
 * transacciones y que los saldos y resúmenes salgan de las consultas SQL. Usa marzo de 2025, un
 * periodo cerrado, para no depender de la fecha en que corren las pruebas.
 */
@IntegrationTest
class FinanceUseCasesIntegrationTests {

    @Autowired
    SetUpFinance setUpFinance;

    @Autowired
    GetFinanceStatus financeStatus;

    @Autowired
    CreateAccount createAccount;

    @Autowired
    ListAccounts listAccounts;

    @Autowired
    DeleteAccount deleteAccount;

    @Autowired
    ListCategories listCategories;

    @Autowired
    DeleteCategory deleteCategory;

    @Autowired
    RecordTransaction recordTransaction;

    @Autowired
    GetPeriodSummary periodSummary;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private UserId owner;

    @BeforeEach
    void setUpFinanceForANewUser() {
        owner = new TestAccounts(users, passwordHasher).create().id();
        setUpFinance.execute(new SetUpFinanceCommand(owner, true));
    }

    @Test
    void setUpIsIdempotentAndCreatesTheCatalog() {
        int categoriesAfterSetUp = countCategories();

        setUpFinance.execute(new SetUpFinanceCommand(owner, true));

        assertThat(financeStatus.execute(owner).setUp()).isTrue();
        assertThat(countCategories()).isEqualTo(categoriesAfterSetUp).isPositive();
        assertThat(listAccounts.execute(owner).accounts()).extracting(AccountView::kind)
                .containsExactly(AccountKind.OTHER);
    }

    @Test
    void balancesAndPeriodSummaryComeFromTheDatabase() {
        AccountView bank = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.SAVINGS, "Ahorros", "Bancolombia", "1234", cop("1000000"))));
        AccountView card = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Visa", "Bancolombia", "5403", cop("3000000"), cop("0"))));
        CategoryId salary = category("Salario");
        CategoryId food = category("Alimentación");
        CategoryId groceries = category("Mercado");

        record(new IncomeDraft(bank.id(), salary, cop("4000000"), on("2025-03-01")));
        record(new ExpenseDraft(card.id(), groceries, cop("350000.50"), on("2025-03-10")));
        record(new TransferDraft(bank.id(), card.id(), cop("350000.50"), on("2025-03-25")));
        record(new ExpenseDraft(bank.id(), null, cop("20000"), on("2025-02-27")));

        AccountList accounts = listAccounts.execute(owner);
        assertThat(balanceOf(accounts, "Ahorros")).isEqualTo(cop("4629999.50"));
        assertThat(balanceOf(accounts, "Visa")).isEqualTo(cop("0"));
        assertThat(accounts.netWorth()).isEqualTo(cop("4629999.50"));

        PeriodSummary march = periodSummary.execute(owner, PeriodKind.MONTH, LocalDate.of(2025, 3, 15));
        assertThat(march.comparison().current()).isEqualTo(
                new DateRange(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)));
        assertThat(march.current()).isEqualTo(Totals.of(cop("4000000"), cop("350000.50")));
        assertThat(march.previous()).isEqualTo(Totals.of(cop("0"), cop("20000")));
        assertThat(march.expensesByCategory()).containsExactly(new CategoryTotal(food, cop("350000.50")));
    }

    @Test
    void accountsAndCategoriesInUseCannotBeDeleted() {
        AccountView cash = createAccount.execute(new CreateAccountCommand(owner,
                new AccountDetails.Cash("Billetera", cop("50000"))));
        CategoryId groceries = category("Mercado");
        record(new ExpenseDraft(cash.id(), groceries, cop("1000"), on("2025-03-01")));

        assertThatThrownBy(() -> deleteAccount.execute(owner, cash.id())).isInstanceOf(AccountInUseException.class);
        assertThatThrownBy(() -> deleteCategory.execute(owner, groceries)).isInstanceOf(CategoryInUseException.class);
    }

    private void record(TransactionDraft draft) {
        recordTransaction.execute(new RecordTransactionCommand(owner, draft));
    }

    private CategoryId category(String name) {
        return allCategories().stream()
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private int countCategories() {
        return allCategories().size();
    }

    private List<CategoryView> allCategories() {
        return listCategories.execute(owner).stream()
                .flatMap(parent -> Stream.concat(Stream.of(parent), parent.subcategories().stream()))
                .toList();
    }

    private static Money balanceOf(AccountList accounts, String name) {
        return accounts.accounts().stream()
                .filter(account -> account.name().equals(name))
                .findFirst()
                .orElseThrow()
                .balance();
    }

    private static TransactionDetails on(String date) {
        return new TransactionDetails(LocalDate.parse(date), null, null, null);
    }
}
