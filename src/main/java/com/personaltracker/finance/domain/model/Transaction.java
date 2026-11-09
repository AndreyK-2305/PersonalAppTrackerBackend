package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.CurrencyMismatchException;
import com.personaltracker.finance.domain.exception.InvalidAmountException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Movimiento de dinero (ADR-0013). Es una jerarquía sellada: los subtipos comparten monto y detalles,
 * y cada uno define qué productos afecta, con qué signo y cómo cuenta en los reportes. Un movimiento
 * es ingreso o gasto solo si el dinero cruza el límite de los productos propios del usuario.
 *
 * <p>Las fábricas y la edición reciben los productos y las categorías ya cargados del mismo
 * propietario para validarlos; el movimiento solo guarda sus identificadores. El tipo no cambia al
 * editar (ADR-0015).
 */
public abstract sealed class Transaction permits Income, Expense, Transfer, Adjustment {

    private final TransactionId id;
    private final UserId owner;
    private Money amount;
    private TransactionDetails details;
    private final Instant createdAt;
    private Instant updatedAt;
    private TransactionOrigin origin = TransactionOrigin.MANUAL;
    private boolean amountEstimated;

    Transaction(
            TransactionId id,
            UserId owner,
            Money amount,
            TransactionDetails details,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.details = Objects.requireNonNull(details, "details");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public abstract TransactionType type();

    /** Cómo cuenta en los reportes: ingreso, gasto o neutro. */
    public abstract ReportEffect reportEffect();

    /** Cambio que produce en el saldo de cada producto afectado. */
    public abstract List<BalanceEffect> balanceEffects();

    /**
     * Marca un movimiento recién creado como captado de una notificación (ADR-0017).
     * {@code amountEstimated} indica que el monto es una conversión con la TRM y no el valor final.
     */
    public void markCaptured(boolean amountEstimated) {
        this.origin = TransactionOrigin.CAPTURE;
        this.amountEstimated = amountEstimated;
    }

    /** Reconstruye el origen de un movimiento persistido. */
    public void restoreOrigin(TransactionOrigin origin, boolean amountEstimated) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.amountEstimated = amountEstimated;
    }

    /**
     * Aplica el monto y los detalles; cada subclase valida antes sus productos y su categoría. Si el
     * usuario cambia un monto estimado, deja de serlo: escribió el valor real.
     */
    void reviseCommon(Money newAmount, TransactionDetails newDetails, Instant now) {
        if (!amount.equals(newAmount)) {
            amountEstimated = false;
        }
        amount = Objects.requireNonNull(newAmount, "newAmount");
        details = Objects.requireNonNull(newDetails, "newDetails");
        updatedAt = now;
    }

    static Money positive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException();
        }
        return amount;
    }

    /**
     * Valida el producto que se asigna al movimiento. Si ya estaba asignado ({@code current}), no se
     * vuelve a comprobar que acepte movimientos, para poder corregir movimientos de productos
     * archivados (ADR-0015).
     */
    static void validateAccount(
            TransactionType type, UserId owner, Account account, AccountId current, Money amount) {
        requireOwner(owner, account.owner());
        if (!account.id().equals(current)) {
            account.ensureAccepts(type);
        }
        if (!account.currency().equals(amount.currency())) {
            throw new CurrencyMismatchException();
        }
    }

    /**
     * Valida la categoría, que es opcional, y devuelve su identificador o nulo. Igual que con los
     * productos, la que ya estaba asignada no se vuelve a comprobar.
     */
    static CategoryId validateCategory(CategoryKind kind, UserId owner, Category category, CategoryId current) {
        if (category == null) {
            return null;
        }
        requireOwner(owner, category.owner());
        if (!category.id().equals(current)) {
            category.ensureUsableFor(kind);
        }
        return category.id();
    }

    /** Los adaptadores cargan productos y categorías filtrando por propietario: otro dueño es un error de programación. */
    private static void requireOwner(UserId expected, UserId actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("El recurso pertenece a otro usuario");
        }
    }

    public TransactionId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public Money amount() {
        return amount;
    }

    public TransactionDetails details() {
        return details;
    }

    public TransactionOrigin origin() {
        return origin;
    }

    public boolean isAmountEstimated() {
        return amountEstimated;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
