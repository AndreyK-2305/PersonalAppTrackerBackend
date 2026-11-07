package com.personaltracker.finance.domain.model;

import com.personaltracker.finance.domain.exception.InvalidCreditLimitException;
import com.personaltracker.finance.domain.exception.InvalidOpeningBalanceException;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Tarjeta de crédito: pasivo con cupo. Su saldo es negativo mientras haya deuda, así que el
 * patrimonio es la suma directa de los saldos. La deuda inicial se guarda como saldo inicial
 * negativo. Las fechas de corte y de pago llegan en la Etapa 6.
 */
public final class CreditCardAccount extends Account {

    private Institution institution;
    private LastFourDigits lastFourDigits;
    private Money creditLimit;

    private CreditCardAccount(
            AccountId id,
            UserId owner,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money creditLimit,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        super(id, owner, name, currency, openingBalance, archivedAt, createdAt, updatedAt);
        this.institution = Objects.requireNonNull(institution, "institution");
        this.lastFourDigits = lastFourDigits;
        this.creditLimit = Objects.requireNonNull(creditLimit, "creditLimit");
    }

    /** Crea la tarjeta en la moneda del cupo. {@code lastFourDigits} puede ser nulo. */
    public static CreditCardAccount open(
            AccountId id,
            UserId owner,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money creditLimit,
            Money initialDebt,
            Instant now) {
        Currency currency = SupportedCurrencies.require(creditLimit.currency());
        return new CreditCardAccount(id, owner, validName(name), institution, lastFourDigits,
                positiveLimit(creditLimit), currency, openingBalanceFor(initialDebt, currency), null, now, now);
    }

    /** Reconstruye una tarjeta persistida tal como estaba. */
    public static CreditCardAccount restore(
            AccountId id,
            UserId owner,
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money creditLimit,
            Currency currency,
            Money openingBalance,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new CreditCardAccount(id, owner, name, institution, lastFourDigits, creditLimit, currency,
                openingBalance, archivedAt, createdAt, updatedAt);
    }

    public void update(
            String name,
            Institution institution,
            LastFourDigits lastFourDigits,
            Money creditLimit,
            Money initialDebt,
            Instant now) {
        Money newLimit = requireCurrency(positiveLimit(creditLimit), currency());
        Institution newInstitution = Objects.requireNonNull(institution, "institution");
        changeCommon(name, openingBalanceFor(initialDebt, currency()), now);
        this.institution = newInstitution;
        this.lastFourDigits = lastFourDigits;
        this.creditLimit = newLimit;
    }

    /** Deuda con la que se registró la tarjeta, como monto no negativo. */
    public Money initialDebt() {
        return openingBalance().negate();
    }

    /** Deuda vigente para un saldo dado; un saldo positivo es saldo a favor y no genera deuda. */
    public Money debtFor(Money balance) {
        return balance.isNegative() ? balance.negate() : Money.zero(currency());
    }

    /** Cupo disponible para un saldo dado: el cupo más el saldo (negativo mientras hay deuda). */
    public Money availableCreditFor(Money balance) {
        return creditLimit.plus(balance);
    }

    @Override
    public AccountKind kind() {
        return AccountKind.CREDIT_CARD;
    }

    private static Money positiveLimit(Money creditLimit) {
        if (!creditLimit.isPositive()) {
            throw new InvalidCreditLimitException();
        }
        return creditLimit;
    }

    private static Money openingBalanceFor(Money initialDebt, Currency currency) {
        if (initialDebt.isNegative()) {
            throw InvalidOpeningBalanceException.negativeDebt();
        }
        return requireCurrency(initialDebt, currency).negate();
    }

    public Institution institution() {
        return institution;
    }

    /** Puede ser nulo. */
    public LastFourDigits lastFourDigits() {
        return lastFourDigits;
    }

    public Money creditLimit() {
        return creditLimit;
    }
}
