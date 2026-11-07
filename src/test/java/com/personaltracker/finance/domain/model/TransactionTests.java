package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.LATER;
import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.TODAY;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static com.personaltracker.finance.domain.model.FinanceTestData.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.domain.exception.AccountArchivedException;
import com.personaltracker.finance.domain.exception.AccountNotAllowedException;
import com.personaltracker.finance.domain.exception.CategoryArchivedException;
import com.personaltracker.finance.domain.exception.CategoryKindMismatchException;
import com.personaltracker.finance.domain.exception.CurrencyMismatchException;
import com.personaltracker.finance.domain.exception.InvalidAmountException;
import com.personaltracker.finance.domain.exception.SameAccountTransferException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionTests {

    private final UserId owner = UserId.newId();
    private final DepositAccount bank = FinanceTestData.savings(owner, "Ahorros");
    private final CreditCardAccount card = FinanceTestData.creditCard(owner);
    private final CashAccount cash = FinanceTestData.cash(owner);
    private final OtherAccount other = FinanceTestData.other(owner);
    private final Category salary = FinanceTestData.category(owner, CategoryKind.INCOME, "Salario");
    private final Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");

    @Nested
    class IncomeRules {

        @Test
        void addsToTheDestinationAndCountsAsIncome() {
            Income income = Income.record(TransactionId.newId(), owner, bank, salary, cop("4500000"), on(TODAY), NOW);

            assertThat(income.type()).isEqualTo(TransactionType.INCOME);
            assertThat(income.reportEffect()).isEqualTo(ReportEffect.INCOME);
            assertThat(income.balanceEffects()).containsExactly(new BalanceEffect(bank.id(), cop("4500000")));
            assertThat(income.category()).isEqualTo(salary.id());
        }

        @Test
        void categoryIsOptional() {
            Income income = Income.record(TransactionId.newId(), owner, bank, null, cop("1000"), on(TODAY), NOW);

            assertThat(income.category()).isNull();
        }

        @Test
        void requiresAnIncomeCategory() {
            assertThatThrownBy(() -> Income.record(TransactionId.newId(), owner, bank, food, cop("1000"), on(TODAY), NOW))
                    .isInstanceOf(CategoryKindMismatchException.class);
        }

        @Test
        void canGoToTheOtherAccount() {
            Income income = Income.record(TransactionId.newId(), owner, other, salary, cop("1000"), on(TODAY), NOW);

            assertThat(income.destination()).isEqualTo(other.id());
        }
    }

    @Nested
    class ExpenseRules {

        @Test
        void subtractsFromTheSourceAndCountsAsExpense() {
            Expense expense = Expense.record(TransactionId.newId(), owner, card, food, cop("85000"),
                    new TransactionDetails(TODAY, "Almuerzo", "Crepes & Waffles", PaymentChannel.CARD), NOW);

            assertThat(expense.reportEffect()).isEqualTo(ReportEffect.EXPENSE);
            assertThat(expense.balanceEffects()).containsExactly(new BalanceEffect(card.id(), cop("-85000")));
            assertThat(expense.details().merchant()).isEqualTo("Crepes & Waffles");
        }

        @Test
        void requiresAnExpenseCategory() {
            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, cash, salary, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(CategoryKindMismatchException.class)
                    .extracting("code").isEqualTo("finance.category.kind_mismatch");
        }

        @Test
        void rejectsArchivedCategories() {
            food.archive(NOW);

            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, cash, food, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(CategoryArchivedException.class);
        }

        @Test
        void canComeFromTheOtherAccount() {
            Expense expense = Expense.record(TransactionId.newId(), owner, other, food, cop("1"), on(TODAY), NOW);

            assertThat(expense.source()).isEqualTo(other.id());
        }
    }

    @Nested
    class TransferRules {

        @Test
        void movesMoneyBetweenOwnAccountsWithoutCountingInReports() {
            Transfer payment = Transfer.record(TransactionId.newId(), owner, bank, card, cop("300000"), on(TODAY), NOW);

            assertThat(payment.reportEffect()).isEqualTo(ReportEffect.NEUTRAL);
            assertThat(payment.balanceEffects()).containsExactly(
                    new BalanceEffect(bank.id(), cop("-300000")), new BalanceEffect(card.id(), cop("300000")));
        }

        @Test
        void requiresTwoDifferentAccounts() {
            assertThatThrownBy(() -> Transfer.record(TransactionId.newId(), owner, bank, bank, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(SameAccountTransferException.class)
                    .extracting("code").isEqualTo("finance.transfer.same_account");
        }

        @Test
        void rejectsTheOtherAccountOnEitherSide() {
            assertThatThrownBy(() -> Transfer.record(TransactionId.newId(), owner, other, bank, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(AccountNotAllowedException.class);
            assertThatThrownBy(() -> Transfer.record(TransactionId.newId(), owner, bank, other, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(AccountNotAllowedException.class);
        }
    }

    @Nested
    class AdjustmentRules {

        @Test
        void increasesOrDecreasesTheBalanceWithoutCountingInReports() {
            Adjustment up = Adjustment.record(TransactionId.newId(), owner, bank, AdjustmentDirection.INCREASE,
                    cop("1500"), on(TODAY), NOW);
            Adjustment down = Adjustment.record(TransactionId.newId(), owner, bank, AdjustmentDirection.DECREASE,
                    cop("700"), on(TODAY), NOW);

            assertThat(up.reportEffect()).isEqualTo(ReportEffect.NEUTRAL);
            assertThat(up.balanceEffects()).containsExactly(new BalanceEffect(bank.id(), cop("1500")));
            assertThat(down.balanceEffects()).containsExactly(new BalanceEffect(bank.id(), cop("-700")));
        }

        @Test
        void rejectsTheOtherAccount() {
            assertThatThrownBy(() -> Adjustment.record(TransactionId.newId(), owner, other,
                    AdjustmentDirection.INCREASE, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(AccountNotAllowedException.class);
        }
    }

    @Nested
    class CommonRules {

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        void requiresAPositiveAmount(String amount) {
            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, cash, food, cop(amount), on(TODAY), NOW))
                    .isInstanceOf(InvalidAmountException.class)
                    .extracting("code").isEqualTo("finance.transaction.amount_invalid");
        }

        @Test
        void requiresTheAccountCurrency() {
            Money dollars = new Money(BigDecimal.TEN, Currency.getInstance("USD"));

            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, cash, food, dollars, on(TODAY), NOW))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void rejectsNewTransactionsOnArchivedAccounts() {
            cash.archive(NOW);

            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, cash, food, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(AccountArchivedException.class);
        }

        @Test
        void rejectsResourcesOfAnotherUser() {
            CashAccount foreign = FinanceTestData.cash(UserId.newId());

            assertThatThrownBy(() -> Expense.record(TransactionId.newId(), owner, foreign, null, cop("1"), on(TODAY), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void eachTypeHasItsOwnReportEffect() {
            List<Transaction> transactions = List.of(
                    Income.record(TransactionId.newId(), owner, bank, null, cop("10"), on(TODAY), NOW),
                    Expense.record(TransactionId.newId(), owner, bank, null, cop("10"), on(TODAY), NOW),
                    Transfer.record(TransactionId.newId(), owner, bank, cash, cop("10"), on(TODAY), NOW),
                    Adjustment.record(TransactionId.newId(), owner, bank, AdjustmentDirection.DECREASE, cop("10"),
                            on(TODAY), NOW));

            assertThat(transactions).extracting(Transaction::reportEffect).containsExactly(
                    ReportEffect.INCOME, ReportEffect.EXPENSE, ReportEffect.NEUTRAL, ReportEffect.NEUTRAL);
        }
    }

    @Nested
    class Revision {

        @Test
        void changesAmountAccountCategoryAndDetails() {
            Expense expense = Expense.record(TransactionId.newId(), owner, other, null, cop("20000"), on(TODAY), NOW);

            expense.revise(card, food, cop("25000"),
                    new TransactionDetails(TODAY.minusDays(1), "Mercado", "Éxito", PaymentChannel.CARD), LATER);

            assertThat(expense.source()).isEqualTo(card.id());
            assertThat(expense.category()).isEqualTo(food.id());
            assertThat(expense.amount()).isEqualTo(cop("25000"));
            assertThat(expense.details().date()).isEqualTo(TODAY.minusDays(1));
            assertThat(expense.updatedAt()).isEqualTo(LATER);
            assertThat(expense.createdAt()).isEqualTo(NOW);
        }

        @Test
        void keepsAnArchivedAccountOrCategoryThatWasAlreadyAssigned() {
            Expense expense = Expense.record(TransactionId.newId(), owner, cash, food, cop("20000"), on(TODAY), NOW);
            cash.archive(LATER);
            food.archive(LATER);

            expense.revise(cash, food, cop("18000"), on(TODAY), LATER);

            assertThat(expense.amount()).isEqualTo(cop("18000"));
        }

        @Test
        void rejectsMovingToAnArchivedAccount() {
            Expense expense = Expense.record(TransactionId.newId(), owner, cash, food, cop("20000"), on(TODAY), NOW);
            bank.archive(LATER);

            assertThatThrownBy(() -> expense.revise(bank, food, cop("20000"), on(TODAY), LATER))
                    .isInstanceOf(AccountArchivedException.class);
        }

        @Test
        void leavesTheTransactionUntouchedWhenInvalid() {
            Transfer transfer = Transfer.record(TransactionId.newId(), owner, bank, card, cop("100"), on(TODAY), NOW);

            assertThatThrownBy(() -> transfer.revise(bank, bank, cop("999"), on(TODAY), LATER))
                    .isInstanceOf(SameAccountTransferException.class);

            assertThat(transfer.amount()).isEqualTo(cop("100"));
            assertThat(transfer.destination()).isEqualTo(card.id());
            assertThat(transfer.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void adjustmentCanChangeDirection() {
            Adjustment adjustment = Adjustment.record(TransactionId.newId(), owner, bank,
                    AdjustmentDirection.INCREASE, cop("100"), on(TODAY), NOW);

            adjustment.revise(bank, AdjustmentDirection.DECREASE, cop("100"), on(TODAY), LATER);

            assertThat(adjustment.balanceEffects()).containsExactly(new BalanceEffect(bank.id(), cop("-100")));
        }
    }
}
