package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ajuste de saldo: corrige el saldo de un producto para cuadrarlo con el banco sin inventar ingresos
 * ni gastos. No cuenta en los reportes.
 */
public final class Adjustment extends Transaction {

    private AccountId account;
    private AdjustmentDirection direction;

    private Adjustment(
            TransactionId id,
            UserId owner,
            AccountId account,
            AdjustmentDirection direction,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, amount, details, createdAt, updatedAt);
        this.account = Objects.requireNonNull(account, "account");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public static Adjustment record(
            TransactionId id,
            UserId owner,
            Account account,
            AdjustmentDirection direction,
            Money amount,
            TransactionDetails details,
            Instant now) {
        Money validAmount = positive(amount);
        validateAccount(TransactionType.ADJUSTMENT, owner, account, null, validAmount);
        return new Adjustment(id, owner, account.id(), direction, validAmount, details, now, now);
    }

    /** Reconstruye un ajuste persistido tal como estaba. */
    public static Adjustment restore(
            TransactionId id,
            UserId owner,
            AccountId account,
            AdjustmentDirection direction,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        return new Adjustment(id, owner, account, direction, amount, details, createdAt, updatedAt);
    }

    public void revise(
            Account account, AdjustmentDirection direction, Money amount, TransactionDetails details, Instant now) {
        Money validAmount = positive(amount);
        validateAccount(type(), owner(), account, this.account, validAmount);
        AdjustmentDirection newDirection = Objects.requireNonNull(direction, "direction");
        reviseCommon(validAmount, details, now);
        this.account = account.id();
        this.direction = newDirection;
    }

    @Override
    public TransactionType type() {
        return TransactionType.ADJUSTMENT;
    }

    @Override
    public ReportEffect reportEffect() {
        return ReportEffect.NEUTRAL;
    }

    @Override
    public List<BalanceEffect> balanceEffects() {
        Money change = direction == AdjustmentDirection.INCREASE ? amount() : amount().negate();
        return List.of(new BalanceEffect(account, change));
    }

    public AccountId account() {
        return account;
    }

    public AdjustmentDirection direction() {
        return direction;
    }
}
