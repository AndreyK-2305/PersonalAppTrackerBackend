package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.SameAccountTransferException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Transferencia entre dos productos propios distintos y con saldo. No es ingreso ni gasto: el pago
 * de una tarjeta y depositar el propio efectivo en el banco son transferencias.
 */
public final class Transfer extends Transaction {

    private AccountId source;
    private AccountId destination;

    private Transfer(
            TransactionId id,
            UserId owner,
            AccountId source,
            AccountId destination,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, amount, details, createdAt, updatedAt);
        this.source = Objects.requireNonNull(source, "source");
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    public static Transfer record(
            TransactionId id,
            UserId owner,
            Account source,
            Account destination,
            Money amount,
            TransactionDetails details,
            Instant now) {
        Money validAmount = positive(amount);
        validateAccounts(owner, source, destination, null, null, validAmount);
        return new Transfer(id, owner, source.id(), destination.id(), validAmount, details, now, now);
    }

    /** Reconstruye una transferencia persistida tal como estaba. */
    public static Transfer restore(
            TransactionId id,
            UserId owner,
            AccountId source,
            AccountId destination,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        return new Transfer(id, owner, source, destination, amount, details, createdAt, updatedAt);
    }

    public void revise(Account source, Account destination, Money amount, TransactionDetails details, Instant now) {
        Money validAmount = positive(amount);
        validateAccounts(owner(), source, destination, this.source, this.destination, validAmount);
        reviseCommon(validAmount, details, now);
        this.source = source.id();
        this.destination = destination.id();
    }

    /** Ambos productos deben estar en la moneda del monto, así que también comparten moneda entre sí. */
    private static void validateAccounts(
            UserId owner,
            Account source,
            Account destination,
            AccountId currentSource,
            AccountId currentDestination,
            Money amount) {
        if (source.id().equals(destination.id())) {
            throw new SameAccountTransferException();
        }
        validateAccount(TransactionType.TRANSFER, owner, source, currentSource, amount);
        validateAccount(TransactionType.TRANSFER, owner, destination, currentDestination, amount);
    }

    @Override
    public TransactionType type() {
        return TransactionType.TRANSFER;
    }

    @Override
    public ReportEffect reportEffect() {
        return ReportEffect.NEUTRAL;
    }

    @Override
    public List<BalanceEffect> balanceEffects() {
        return List.of(new BalanceEffect(source, amount().negate()), new BalanceEffect(destination, amount()));
    }

    public AccountId source() {
        return source;
    }

    public AccountId destination() {
        return destination;
    }
}
