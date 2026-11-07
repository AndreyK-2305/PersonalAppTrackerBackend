package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Ingreso: dinero que entra a un producto propio desde afuera. La categoría es opcional. */
public final class Income extends Transaction {

    private AccountId destination;
    private CategoryId category;

    private Income(
            TransactionId id,
            UserId owner,
            AccountId destination,
            CategoryId category,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, amount, details, createdAt, updatedAt);
        this.destination = Objects.requireNonNull(destination, "destination");
        this.category = category;
    }

    /** {@code category} puede ser nula. */
    public static Income record(
            TransactionId id,
            UserId owner,
            Account destination,
            Category category,
            Money amount,
            TransactionDetails details,
            Instant now) {
        Money validAmount = positive(amount);
        validateAccount(TransactionType.INCOME, owner, destination, null, validAmount);
        CategoryId categoryId = validateCategory(CategoryKind.INCOME, owner, category, null);
        return new Income(id, owner, destination.id(), categoryId, validAmount, details, now, now);
    }

    /** Reconstruye un ingreso persistido tal como estaba. */
    public static Income restore(
            TransactionId id,
            UserId owner,
            AccountId destination,
            CategoryId category,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        return new Income(id, owner, destination, category, amount, details, createdAt, updatedAt);
    }

    /** {@code category} puede ser nula. */
    public void revise(Account destination, Category category, Money amount, TransactionDetails details, Instant now) {
        Money validAmount = positive(amount);
        validateAccount(type(), owner(), destination, this.destination, validAmount);
        CategoryId categoryId = validateCategory(CategoryKind.INCOME, owner(), category, this.category);
        reviseCommon(validAmount, details, now);
        this.destination = destination.id();
        this.category = categoryId;
    }

    @Override
    public TransactionType type() {
        return TransactionType.INCOME;
    }

    @Override
    public ReportEffect reportEffect() {
        return ReportEffect.INCOME;
    }

    @Override
    public List<BalanceEffect> balanceEffects() {
        return List.of(new BalanceEffect(destination, amount()));
    }

    public AccountId destination() {
        return destination;
    }

    /** Puede ser nula. */
    public CategoryId category() {
        return category;
    }
}
