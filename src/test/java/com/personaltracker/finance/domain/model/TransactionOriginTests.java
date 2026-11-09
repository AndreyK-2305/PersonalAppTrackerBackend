package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.LATER;
import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static com.personaltracker.finance.domain.model.FinanceTestData.TODAY;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static com.personaltracker.finance.domain.model.FinanceTestData.on;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.Test;

class TransactionOriginTests {

    private final UserId owner = UserId.newId();
    private final CreditCardAccount card = FinanceTestData.creditCard(owner);

    private Expense expense() {
        return Expense.record(TransactionId.newId(), owner, card, null, cop("63493.40"), on(TODAY), NOW);
    }

    @Test
    void manualByDefault() {
        Expense expense = expense();

        assertThat(expense.origin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(expense.isAmountEstimated()).isFalse();
    }

    @Test
    void anEstimatedAmountStopsBeingEstimatedWhenTheUserChangesIt() {
        Expense expense = expense();
        expense.markCaptured(true);

        expense.revise(card, null, cop("63493.40"), on(TODAY.minusDays(1)), LATER);
        assertThat(expense.isAmountEstimated()).isTrue();

        expense.revise(card, null, cop("65120.00"), on(TODAY), LATER);
        assertThat(expense.isAmountEstimated()).isFalse();
        assertThat(expense.origin()).isEqualTo(TransactionOrigin.CAPTURE);
    }
}
