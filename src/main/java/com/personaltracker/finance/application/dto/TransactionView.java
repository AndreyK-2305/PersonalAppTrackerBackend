package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Movimiento en forma plana. Según el tipo, son nulos {@code sourceAccount} (ingreso),
 * {@code destinationAccount} (gasto), {@code category} (transferencia, ajuste o sin categoría) y
 * {@code direction} (todo salvo ajustes). Un ajuste usa {@code destinationAccount} si aumenta el saldo
 * y {@code sourceAccount} si lo disminuye.
 */
public record TransactionView(
        TransactionId id,
        TransactionType type,
        Money amount,
        LocalDate date,
        String description,
        String merchant,
        PaymentChannel channel,
        AccountId sourceAccount,
        AccountId destinationAccount,
        CategoryId category,
        AdjustmentDirection direction,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionView of(Transaction transaction) {
        return switch (transaction) {
            case Income income -> view(income, null, income.destination(), income.category(), null);
            case Expense expense -> view(expense, expense.source(), null, expense.category(), null);
            case Transfer transfer -> view(transfer, transfer.source(), transfer.destination(), null, null);
            case Adjustment adjustment -> adjustment.direction() == AdjustmentDirection.INCREASE
                    ? view(adjustment, null, adjustment.account(), null, adjustment.direction())
                    : view(adjustment, adjustment.account(), null, null, adjustment.direction());
        };
    }

    private static TransactionView view(
            Transaction transaction,
            AccountId source,
            AccountId destination,
            CategoryId category,
            AdjustmentDirection direction) {
        return new TransactionView(
                transaction.id(),
                transaction.type(),
                transaction.amount(),
                transaction.details().date(),
                transaction.details().description(),
                transaction.details().merchant(),
                transaction.details().channel(),
                source,
                destination,
                category,
                direction,
                transaction.createdAt(),
                transaction.updatedAt());
    }
}
