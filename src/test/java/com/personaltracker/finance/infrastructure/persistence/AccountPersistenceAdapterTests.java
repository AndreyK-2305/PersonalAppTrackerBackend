package com.personaltracker.finance.infrastructure.persistence;

import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.FinanceTestData;
import com.personaltracker.finance.domain.model.Institution;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.finance.domain.model.SupportedCurrencies;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class AccountPersistenceAdapterTests {

    @Autowired
    AccountRepository accounts;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private UserId owner;

    @BeforeEach
    void createOwner() {
        owner = new TestAccounts(users, passwordHasher).create().id();
    }

    @Test
    void savesAndRestoresEveryKindOfAccount() {
        CashAccount cash = FinanceTestData.cash(owner);
        DepositAccount wallet = DepositAccount.open(AccountId.newId(), owner, AccountKind.DIGITAL_WALLET, "Nequi",
                new Institution("Nequi"), null, cop("-1500.50"), NOW);
        CreditCardAccount card = FinanceTestData.creditCard(owner);
        card.archive(NOW);
        OtherAccount other = FinanceTestData.other(owner);
        List<Account> saved = List.of(cash, wallet, card, other);

        saved.forEach(accounts::save);

        for (Account account : saved) {
            assertThat(accounts.findByIdAndOwner(account.id(), owner).orElseThrow())
                    .usingRecursiveComparison()
                    .isEqualTo(account);
        }
        assertThat(accounts.findAllByOwner(owner)).extracting(Account::id)
                .containsExactlyInAnyOrder(cash.id(), wallet.id(), card.id(), other.id());
        assertThat(accounts.findOtherAccount(owner)).get().extracting(Account::id).isEqualTo(other.id());
    }

    @Test
    void updatesAnExistingAccount() {
        CreditCardAccount card = FinanceTestData.creditCard(owner);
        accounts.save(card);

        CreditCardAccount loaded = (CreditCardAccount) accounts.findByIdAndOwner(card.id(), owner).orElseThrow();
        loaded.update("Visa Oro", new Institution("Davivienda"), null, cop("8000000"), cop("120000"), NOW);
        accounts.save(loaded);

        assertThat(accounts.findByIdAndOwner(card.id(), owner).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(loaded);
    }

    @Test
    void comparesNamesIgnoringCaseAndCanExcludeTheAccountItself() {
        CashAccount cash = FinanceTestData.cash(owner);
        accounts.save(cash);

        assertThat(accounts.existsByOwnerAndName(owner, "BILLETERA", null)).isTrue();
        assertThat(accounts.existsByOwnerAndName(owner, "billetera", cash.id())).isFalse();
        assertThat(accounts.existsByOwnerAndName(owner, "Ahorros", null)).isFalse();
    }

    @Test
    void neverReturnsAnotherUsersAccounts() {
        CashAccount cash = FinanceTestData.cash(owner);
        accounts.save(cash);
        UserId intruder = new TestAccounts(users, passwordHasher).create().id();

        assertThat(accounts.findByIdAndOwner(cash.id(), intruder)).isEmpty();
        assertThat(accounts.findAllByOwner(intruder)).isEmpty();
        assertThat(accounts.existsByOwnerAndName(intruder, "Billetera", null)).isFalse();
    }

    @Test
    void deletesAnAccount() {
        CashAccount cash = FinanceTestData.cash(owner);
        accounts.save(cash);

        accounts.delete(cash);

        assertThat(accounts.findByIdAndOwner(cash.id(), owner)).isEmpty();
    }

    @Test
    void theDatabaseRejectsDuplicateNamesAndASecondOtherAccount() {
        accounts.save(FinanceTestData.cash(owner));
        accounts.save(FinanceTestData.other(owner));

        assertThatThrownBy(() -> accounts.save(
                CashAccount.open(AccountId.newId(), owner, "BILLETERA", cop("0"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> accounts.save(
                OtherAccount.createFor(AccountId.newId(), owner, SupportedCurrencies.COP, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
