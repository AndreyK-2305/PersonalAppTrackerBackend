package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.FinanceFakes.CLOCK;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.FinanceFakes.InMemoryAccountRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryCategoryRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryTransactionRepository;
import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountList;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.application.dto.TransactionDraft.TransferDraft;
import com.personaltracker.finance.application.dto.UpdateAccountCommand;
import com.personaltracker.finance.domain.exception.AccountInUseException;
import com.personaltracker.finance.domain.exception.AccountKindChangeException;
import com.personaltracker.finance.domain.exception.AccountNameTakenException;
import com.personaltracker.finance.domain.exception.AccountNotFoundException;
import com.personaltracker.finance.domain.exception.InvalidInstitutionException;
import com.personaltracker.finance.domain.exception.InvalidLastFourDigitsException;
import com.personaltracker.finance.domain.exception.SystemManagedAccountException;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountUseCasesTests {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 6);

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final CreateAccount create = new CreateAccount(accounts, CLOCK);
    private final UpdateAccount update = new UpdateAccount(accounts, transactions, CLOCK);
    private final ArchiveAccount archive = new ArchiveAccount(accounts, transactions, CLOCK);
    private final ReactivateAccount reactivate = new ReactivateAccount(accounts, transactions, CLOCK);
    private final DeleteAccount delete = new DeleteAccount(accounts, transactions);
    private final ListAccounts list = new ListAccounts(accounts, transactions);
    private final RecordTransaction record = new RecordTransaction(transactions, accounts, categories, CLOCK);
    private final UserId owner = UserId.newId();

    @Test
    void createsEachKindOfAccount() {
        AccountView cash = create.execute(new CreateAccountCommand(owner, new AccountDetails.Cash("Billetera", cop("80000"))));
        AccountView wallet = create.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.DIGITAL_WALLET, "Nequi", "Nequi", " ", cop("15000"))));
        AccountView card = create.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Mastercard", "Bancolombia", "5403", cop("5000000"), cop("200000"))));

        assertThat(cash.kind()).isEqualTo(AccountKind.CASH);
        assertThat(cash.balance()).isEqualTo(cop("80000"));
        assertThat(wallet.institution()).isEqualTo("Nequi");
        assertThat(wallet.lastFourDigits()).isNull();
        assertThat(card.balance()).isEqualTo(cop("-200000"));
        assertThat(card.debt()).isEqualTo(cop("200000"));
        assertThat(card.availableCredit()).isEqualTo(cop("4800000"));
        assertThat(card.lastFourDigits()).isEqualTo("5403");
    }

    @Test
    void validatesInstitutionAndDigits() {
        assertThatThrownBy(() -> create.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.SAVINGS, "Ahorros", " ", null, cop("0")))))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThatThrownBy(() -> create.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Visa", "Bancolombia", "4111111111111111", cop("1"), cop("0")))))
                .isInstanceOf(InvalidLastFourDigitsException.class);
        assertThat(accounts.count()).isZero();
    }

    @Test
    void namesAreUniquePerUserIgnoringCase() {
        createCash("Billetera");

        assertThatThrownBy(() -> createCash(" billetera "))
                .isInstanceOf(AccountNameTakenException.class)
                .extracting("code").isEqualTo("finance.account.name_taken");

        UserId someoneElse = UserId.newId();
        create.execute(new CreateAccountCommand(someoneElse, new AccountDetails.Cash("Billetera", cop("0"))));
        assertThat(accounts.count()).isEqualTo(2);
    }

    @Test
    void theOtherAccountReservesItsName() {
        new SetUpFinance(accounts, categories, CLOCK).execute(new SetUpFinanceCommand(owner, false));

        assertThatThrownBy(() -> createCash("otro")).isInstanceOf(AccountNameTakenException.class);
    }

    @Test
    void updatesKeepingTheKind() {
        AccountView savings = create.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.SAVINGS, "Ahorros", "Bancolombia", "1234", cop("0"))));

        AccountView updated = update.execute(new UpdateAccountCommand(owner, savings.id(),
                new AccountDetails.Deposit(AccountKind.CHECKING, "Corriente", "Bancolombia", null, cop("100"))));

        assertThat(updated.kind()).isEqualTo(AccountKind.CHECKING);
        assertThat(updated.name()).isEqualTo("Corriente");
        assertThat(updated.balance()).isEqualTo(cop("100"));
        assertThatThrownBy(() -> update.execute(new UpdateAccountCommand(owner, savings.id(),
                new AccountDetails.Cash("Efectivo", cop("0")))))
                .isInstanceOf(AccountKindChangeException.class);
    }

    @Test
    void updateKeepsItsOwnNameButRejectsAnotherAccountsName() {
        AccountView cash = createCash("Billetera");
        createCash("Alcancía");

        update.execute(new UpdateAccountCommand(owner, cash.id(), new AccountDetails.Cash("BILLETERA", cop("1"))));

        assertThatThrownBy(() -> update.execute(new UpdateAccountCommand(owner, cash.id(),
                new AccountDetails.Cash("alcancía", cop("1")))))
                .isInstanceOf(AccountNameTakenException.class);
    }

    @Test
    void theOtherAccountCannotBeEditedArchivedOrDeleted() {
        new SetUpFinance(accounts, categories, CLOCK).execute(new SetUpFinanceCommand(owner, false));
        AccountId other = accounts.findOtherAccount(owner).orElseThrow().id();

        assertThatThrownBy(() -> update.execute(new UpdateAccountCommand(owner, other,
                new AccountDetails.Cash("Mío", cop("0")))))
                .isInstanceOf(SystemManagedAccountException.class);
        assertThatThrownBy(() -> archive.execute(owner, other)).isInstanceOf(SystemManagedAccountException.class);
        assertThatThrownBy(() -> delete.execute(owner, other)).isInstanceOf(SystemManagedAccountException.class);
    }

    @Test
    void archivesAndReactivates() {
        AccountView cash = createCash("Billetera");

        assertThat(archive.execute(owner, cash.id()).archived()).isTrue();
        assertThat(reactivate.execute(owner, cash.id()).archived()).isFalse();
    }

    @Test
    void deletesOnlyAccountsWithoutTransactions() {
        AccountView empty = createCash("Vacía");
        AccountView used = createCash("Usada");
        record.execute(new RecordTransactionCommand(owner, new ExpenseDraft(used.id(), null, cop("1000"), on())));

        delete.execute(owner, empty.id());

        assertThat(accounts.findByIdAndOwner(empty.id(), owner)).isEmpty();
        assertThatThrownBy(() -> delete.execute(owner, used.id()))
                .isInstanceOf(AccountInUseException.class)
                .extracting("code").isEqualTo("finance.account.has_transactions");
    }

    @Test
    void anotherUsersAccountIsNotFound() {
        AccountView cash = createCash("Billetera");
        UserId intruder = UserId.newId();

        assertThatThrownBy(() -> archive.execute(intruder, cash.id())).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> delete.execute(intruder, cash.id())).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> update.execute(new UpdateAccountCommand(intruder, cash.id(),
                new AccountDetails.Cash("Mía", cop("0")))))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(list.execute(intruder).accounts()).isEmpty();
    }

    @Test
    void listsBalancesAndNetWorthExcludingTheOtherAccount() {
        new SetUpFinance(accounts, categories, CLOCK).execute(new SetUpFinanceCommand(owner, false));
        AccountView bank = create.execute(new CreateAccountCommand(owner,
                new AccountDetails.Deposit(AccountKind.SAVINGS, "Ahorros", "Bancolombia", null, cop("1000000"))));
        AccountView card = create.execute(new CreateAccountCommand(owner,
                new AccountDetails.CreditCard("Visa", "Bancolombia", null, cop("2000000"), cop("0"))));
        AccountView archived = createCash("Alcancía");
        archive.execute(owner, archived.id());
        AccountId other = accounts.findOtherAccount(owner).orElseThrow().id();
        record.execute(new RecordTransactionCommand(owner, new ExpenseDraft(card.id(), null, cop("300000"), on())));
        record.execute(new RecordTransactionCommand(owner, new TransferDraft(bank.id(), card.id(), cop("100000"), on())));
        record.execute(new RecordTransactionCommand(owner, new ExpenseDraft(other, null, cop("50000"), on())));

        AccountList result = list.execute(owner);

        assertThat(result.accounts()).extracting(AccountView::name).containsExactly("Ahorros", "Visa", "Alcancía", "Otro");
        assertThat(result.accounts()).extracting(AccountView::balance)
                .containsExactly(cop("900000"), cop("-200000"), cop("50000"), null);
        assertThat(result.netWorth()).isEqualTo(cop("750000"));
    }

    private AccountView createCash(String name) {
        return create.execute(new CreateAccountCommand(owner, new AccountDetails.Cash(name, cop("50000"))));
    }

    private static TransactionDetails on() {
        return new TransactionDetails(DATE, null, null, null);
    }
}
