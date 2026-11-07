package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.AccountArchivedException;
import com.personaltracker.finance.domain.exception.CurrencyMismatchException;
import com.personaltracker.finance.domain.exception.InvalidAccountNameException;
import com.personaltracker.finance.domain.exception.SystemManagedAccountException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Producto financiero propio: el lugar donde está el dinero (ADR-0013). Es una jerarquía sellada
 * porque cada tipo se comporta distinto: el efectivo y las cuentas de depósito son activos, la
 * tarjeta de crédito es un pasivo con cupo y "Otro" no lleva saldo.
 *
 * <p>El saldo no se guarda: se deriva del saldo inicial más los efectos de los movimientos.
 */
public abstract sealed class Account permits CashAccount, DepositAccount, CreditCardAccount, OtherAccount {

    public static final int MAX_NAME_LENGTH = 60;

    private final AccountId id;
    private final UserId owner;
    private String name;
    private final Currency currency;
    private Money openingBalance;
    private Instant archivedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    Account(
            AccountId id,
            UserId owner,
            String name,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.openingBalance = Objects.requireNonNull(openingBalance, "openingBalance");
        this.archivedAt = archivedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public abstract AccountKind kind();

    /** Si el producto lleva saldo: su saldo se calcula y entra en el patrimonio. */
    public boolean tracksBalance() {
        return true;
    }

    /** Si lo administra el sistema: no se puede editar, archivar ni borrar. */
    public boolean isSystemManaged() {
        return false;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Comprueba que el producto puede recibir un movimiento nuevo del tipo indicado. */
    public void ensureAccepts(TransactionType type) {
        Objects.requireNonNull(type, "type");
        if (isArchived()) {
            throw new AccountArchivedException();
        }
    }

    /** Lo archivado no aparece en los selectores ni recibe movimientos nuevos, pero conserva su historial. */
    public void archive(Instant now) {
        ensureUserManaged();
        if (archivedAt == null) {
            archivedAt = now;
            updatedAt = now;
        }
    }

    public void reactivate(Instant now) {
        ensureUserManaged();
        if (archivedAt != null) {
            archivedAt = null;
            updatedAt = now;
        }
    }

    /** Aplica los cambios comunes; cada subclase valida antes el saldo inicial según su tipo. */
    void changeCommon(String newName, Money newOpeningBalance, Instant now) {
        ensureUserManaged();
        String validName = validName(newName);
        Money validBalance = requireCurrency(newOpeningBalance, currency);
        name = validName;
        openingBalance = validBalance;
        updatedAt = now;
    }

    /** Comprueba que el usuario puede editar, archivar o borrar el producto. */
    public void ensureUserManaged() {
        if (isSystemManaged()) {
            throw new SystemManagedAccountException();
        }
    }

    static String validName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidAccountNameException();
        }
        return trimmed;
    }

    static Money requireCurrency(Money money, Currency expected) {
        if (!money.currency().equals(expected)) {
            throw new CurrencyMismatchException();
        }
        return money;
    }

    public AccountId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Currency currency() {
        return currency;
    }

    public Money openingBalance() {
        return openingBalance;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
