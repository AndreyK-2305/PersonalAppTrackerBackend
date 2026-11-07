package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.LATER;
import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.domain.exception.AccountArchivedException;
import com.personaltracker.finance.domain.exception.AccountNotAllowedException;
import com.personaltracker.finance.domain.exception.CurrencyMismatchException;
import com.personaltracker.finance.domain.exception.InvalidAccountNameException;
import com.personaltracker.finance.domain.exception.InvalidCreditLimitException;
import com.personaltracker.finance.domain.exception.InvalidOpeningBalanceException;
import com.personaltracker.finance.domain.exception.SystemManagedAccountException;
import com.personaltracker.finance.domain.exception.UnsupportedCurrencyException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTests {

    private final UserId owner = UserId.newId();

    @Nested
    class Common {

        @Test
        void trimsTheNameAndRejectsBlankOrTooLongNames() {
            assertThat(CashAccount.open(AccountId.newId(), owner, "  Billetera  ", cop("0"), NOW).name())
                    .isEqualTo("Billetera");

            assertThatThrownBy(() -> CashAccount.open(AccountId.newId(), owner, " ", cop("0"), NOW))
                    .isInstanceOf(InvalidAccountNameException.class);
            assertThatThrownBy(() -> CashAccount.open(AccountId.newId(), owner, "x".repeat(61), cop("0"), NOW))
                    .isInstanceOf(InvalidAccountNameException.class);
        }

        @Test
        void acceptsOnlyColombianPesos() {
            Money dollars = new Money(BigDecimal.TEN, Currency.getInstance("USD"));

            assertThatThrownBy(() -> CashAccount.open(AccountId.newId(), owner, "Dólares", dollars, NOW))
                    .isInstanceOf(UnsupportedCurrencyException.class)
                    .extracting("code").isEqualTo("finance.currency.unsupported");
        }

        @Test
        void archivedAccountsRejectNewTransactionsUntilReactivated() {
            CashAccount cash = FinanceTestData.cash(owner);

            cash.archive(LATER);

            assertThat(cash.isArchived()).isTrue();
            assertThat(cash.updatedAt()).isEqualTo(LATER);
            assertThatThrownBy(() -> cash.ensureAccepts(TransactionType.EXPENSE))
                    .isInstanceOf(AccountArchivedException.class);

            cash.reactivate(LATER);

            assertThat(cash.isArchived()).isFalse();
            assertThatCode(() -> cash.ensureAccepts(TransactionType.EXPENSE)).doesNotThrowAnyException();
        }

        @Test
        void updateLeavesTheAccountUntouchedWhenAValueIsInvalid() {
            CashAccount cash = FinanceTestData.cash(owner);

            assertThatThrownBy(() -> cash.update("Nuevo nombre", cop("-1"), LATER))
                    .isInstanceOf(InvalidOpeningBalanceException.class);

            assertThat(cash.name()).isEqualTo("Billetera");
            assertThat(cash.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class Cash {

        @Test
        void isAnAssetWithANonNegativeOpeningBalance() {
            CashAccount cash = FinanceTestData.cash(owner);

            assertThat(cash.kind()).isEqualTo(AccountKind.CASH);
            assertThat(cash.tracksBalance()).isTrue();
            assertThat(cash.openingBalance()).isEqualTo(cop("50000"));
            assertThatThrownBy(() -> CashAccount.open(AccountId.newId(), owner, "Billetera", cop("-1"), NOW))
                    .isInstanceOf(InvalidOpeningBalanceException.class)
                    .extracting("code").isEqualTo("finance.account.opening_balance_invalid");
        }
    }

    @Nested
    class Deposit {

        @Test
        void allowsAnOverdraftAndOptionalLastFourDigits() {
            DepositAccount checking = DepositAccount.open(AccountId.newId(), owner, AccountKind.CHECKING,
                    "Corriente", new Institution("Davivienda"), null, cop("-20000"), NOW);

            assertThat(checking.kind()).isEqualTo(AccountKind.CHECKING);
            assertThat(checking.openingBalance()).isEqualTo(cop("-20000"));
            assertThat(checking.lastFourDigits()).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = AccountKind.class, names = {"CASH", "CREDIT_CARD", "OTHER"})
        void rejectsKindsThatAreNotDepositAccounts(AccountKind kind) {
            assertThatThrownBy(() -> DepositAccount.open(AccountId.newId(), owner, kind, "Cuenta",
                    new Institution("Nequi"), null, cop("0"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void updatesKindInstitutionAndDigits() {
            DepositAccount account = FinanceTestData.savings(owner, "Ahorros");

            account.update(AccountKind.DIGITAL_WALLET, "Nequi", new Institution("Nequi"), null, cop("10"), LATER);

            assertThat(account.kind()).isEqualTo(AccountKind.DIGITAL_WALLET);
            assertThat(account.name()).isEqualTo("Nequi");
            assertThat(account.institution()).isEqualTo(new Institution("Nequi"));
            assertThat(account.lastFourDigits()).isNull();
            assertThat(account.openingBalance()).isEqualTo(cop("10"));
        }
    }

    @Nested
    class CreditCard {

        @Test
        void storesTheInitialDebtAsANegativeOpeningBalance() {
            CreditCardAccount card = CreditCardAccount.open(AccountId.newId(), owner, "Visa",
                    new Institution("Bancolombia"), new LastFourDigits("5403"), cop("3000000"), cop("450000"), NOW);

            assertThat(card.kind()).isEqualTo(AccountKind.CREDIT_CARD);
            assertThat(card.openingBalance()).isEqualTo(cop("-450000"));
            assertThat(card.initialDebt()).isEqualTo(cop("450000"));
        }

        @Test
        void derivesDebtAndAvailableCreditFromTheBalance() {
            CreditCardAccount card = FinanceTestData.creditCard(owner);

            assertThat(card.debtFor(cop("-1200000"))).isEqualTo(cop("1200000"));
            assertThat(card.availableCreditFor(cop("-1200000"))).isEqualTo(cop("3800000"));
            assertThat(card.debtFor(cop("15000"))).isEqualTo(cop("0"));
            assertThat(card.availableCreditFor(cop("15000"))).isEqualTo(cop("5015000"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        void requiresAPositiveCreditLimit(String limit) {
            assertThatThrownBy(() -> CreditCardAccount.open(AccountId.newId(), owner, "Visa",
                    new Institution("Bancolombia"), null, cop(limit), cop("0"), NOW))
                    .isInstanceOf(InvalidCreditLimitException.class);
        }

        @Test
        void rejectsANegativeInitialDebt() {
            assertThatThrownBy(() -> CreditCardAccount.open(AccountId.newId(), owner, "Visa",
                    new Institution("Bancolombia"), null, cop("1000000"), cop("-1"), NOW))
                    .isInstanceOf(InvalidOpeningBalanceException.class);
        }

        @Test
        void rejectsAnInitialDebtInAnotherCurrency() {
            Money dollars = new Money(BigDecimal.ONE, Currency.getInstance("USD"));

            assertThatThrownBy(() -> CreditCardAccount.open(AccountId.newId(), owner, "Visa",
                    new Institution("Bancolombia"), null, cop("1000000"), dollars, NOW))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void updatesLimitAndInitialDebt() {
            CreditCardAccount card = FinanceTestData.creditCard(owner);

            card.update("Visa Oro", new Institution("Bancolombia"), new LastFourDigits("9876"), cop("7000000"),
                    cop("100000"), LATER);

            assertThat(card.name()).isEqualTo("Visa Oro");
            assertThat(card.lastFourDigits()).isEqualTo(new LastFourDigits("9876"));
            assertThat(card.creditLimit()).isEqualTo(cop("7000000"));
            assertThat(card.initialDebt()).isEqualTo(cop("100000"));
        }
    }

    @Nested
    class Other {

        @Test
        void isASystemAccountWithoutBalance() {
            OtherAccount other = FinanceTestData.other(owner);

            assertThat(other.kind()).isEqualTo(AccountKind.OTHER);
            assertThat(other.name()).isEqualTo("Otro");
            assertThat(other.openingBalance()).isEqualTo(cop("0"));
            assertThat(other.tracksBalance()).isFalse();
            assertThat(other.isSystemManaged()).isTrue();
        }

        @Test
        void acceptsOnlyIncomeAndExpenses() {
            OtherAccount other = FinanceTestData.other(owner);

            assertThatCode(() -> other.ensureAccepts(TransactionType.INCOME)).doesNotThrowAnyException();
            assertThatCode(() -> other.ensureAccepts(TransactionType.EXPENSE)).doesNotThrowAnyException();
            assertThatThrownBy(() -> other.ensureAccepts(TransactionType.TRANSFER))
                    .isInstanceOf(AccountNotAllowedException.class);
            assertThatThrownBy(() -> other.ensureAccepts(TransactionType.ADJUSTMENT))
                    .isInstanceOf(AccountNotAllowedException.class);
        }

        @Test
        void cannotBeArchived() {
            OtherAccount other = FinanceTestData.other(owner);

            assertThatThrownBy(() -> other.archive(LATER))
                    .isInstanceOf(SystemManagedAccountException.class)
                    .extracting("code").isEqualTo("finance.account.system_managed");
            assertThatThrownBy(() -> other.reactivate(LATER)).isInstanceOf(SystemManagedAccountException.class);
            assertThat(other.isArchived()).isFalse();
        }
    }
}
