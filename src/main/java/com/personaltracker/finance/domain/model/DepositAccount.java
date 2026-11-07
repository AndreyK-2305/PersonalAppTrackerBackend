package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Cuenta de ahorros, cuenta corriente o billetera digital de una entidad: activo cuyo saldo inicial
 * puede ser negativo (sobregiro). Los últimos 4 dígitos son opcionales.
 */
public final class DepositAccount extends Account {

    private static final Set<AccountKind> KINDS =
            EnumSet.of(AccountKind.SAVINGS, AccountKind.CHECKING, AccountKind.DIGITAL_WALLET);

    private AccountKind kind;
    private Institution institution;
    private LastFourDigits lastFourDigits;

    private DepositAccount(
            AccountId id,
            UserId owner,
            AccountKind kind,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, name, currency, openingBalance, archivedAt, createdAt, updatedAt);
        this.kind = depositKind(kind);
        this.institution = Objects.requireNonNull(institution, "institution");
        this.lastFourDigits = lastFourDigits;
    }

    /** Crea el producto en la moneda del saldo inicial. {@code lastFourDigits} puede ser nulo. */
    public static DepositAccount open(
            AccountId id,
            UserId owner,
            AccountKind kind,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money openingBalance,
            Instant now) {
        Currency currency = SupportedCurrencies.require(openingBalance.currency());
        return new DepositAccount(id, owner, kind, validName(name), institution, lastFourDigits, currency,
                openingBalance, null, now, now);
    }

    /** Reconstruye un producto persistido tal como estaba. */
    public static DepositAccount restore(
            AccountId id,
            UserId owner,
            AccountKind kind,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new DepositAccount(id, owner, kind, name, institution, lastFourDigits, currency, openingBalance,
                archivedAt, createdAt, updatedAt);
    }

    public void update(
            AccountKind kind,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money openingBalance,
            Instant now) {
        AccountKind newKind = depositKind(kind);
        Institution newInstitution = Objects.requireNonNull(institution, "institution");
        changeCommon(name, openingBalance, now);
        this.kind = newKind;
        this.institution = newInstitution;
        this.lastFourDigits = lastFourDigits;
    }

    /** El tipo llega de la API ya asociado a esta subclase: otro valor es un error de programación. */
    private static AccountKind depositKind(AccountKind kind) {
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("No es un tipo de cuenta de depósito: " + kind);
        }
        return kind;
    }

    @Override
    public AccountKind kind() {
        return kind;
    }

    public Institution institution() {
        return institution;
    }

    /** Puede ser nulo. */
    public LastFourDigits lastFourDigits() {
        return lastFourDigits;
    }
}
