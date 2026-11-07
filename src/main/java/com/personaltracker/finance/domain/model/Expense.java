package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Gasto: dinero que sale de un producto propio hacia afuera. Una compra con tarjeta es un gasto
 * desde la tarjeta y aumenta su deuda. La categoría es opcional.
 */
public final class Expense extends Transaction {

    private AccountId source;
    private CategoryId category;

    private Expense(
            TransactionId id,
            UserId owner,
            AccountId source,
            CategoryId category,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, amount, details, createdAt, updatedAt);
        this.source = Objects.requireNonNull(source, "source");
        this.category = category;
    }

    /** {@code category} puede ser nula. */
    public static Expense record(
            TransactionId id,
            UserId owner,
            Account source,
            Category category,
            Money amount,
            TransactionDetails details,
            Instant now) {
        Money validAmount = positive(amount);
        validateAccount(TransactionType.EXPENSE, owner, source, null, validAmount);
        CategoryId categoryId = validateCategory(CategoryKind.EXPENSE, owner, category, null);
        return new Expense(id, owner, source.id(), categoryId, validAmount, details, now, now);
    }

    /** Reconstruye un gasto persistido tal como estaba. */
    public static Expense restore(
            TransactionId id,
            UserId owner,
            AccountId source,
            CategoryId category,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        return new Expense(id, owner, source, category, amount, details, createdAt, updatedAt);
    }

    /** {@code category} puede ser nula. */
    public void revise(Account source, Category category, Money amount, TransactionDetails details, Instant now) {
        Money validAmount = positive(amount);
        validateAccount(type(), owner(), source, this.source, validAmount);
        CategoryId categoryId = validateCategory(CategoryKind.EXPENSE, owner(), category, this.category);
        reviseCommon(validAmount, details, now);
        this.source = source.id();
        this.category = categoryId;
    }

    @Override
    public TransactionType type() {
        return TransactionType.EXPENSE;
    }

    @Override
    public ReportEffect reportEffect() {
        return ReportEffect.EXPENSE;
    }

    @Override
    public List<BalanceEffect> balanceEffects() {
        return List.of(new BalanceEffect(source, amount().negate()));
    }

    public AccountId source() {
        return source;
    }

    /** Puede ser nula. */
    public CategoryId category() {
        return category;
    }
}
