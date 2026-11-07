package com.personaltracker.finance.domain.model;

import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Datos de prueba del dominio financiero. */
public final class FinanceTestData {

    public static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    public static final Instant LATER = NOW.plusSeconds(3600);
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private FinanceTestData() {
    }

    public static Money cop(String amount) {
        return new Money(new BigDecimal(amount), SupportedCurrencies.COP);
    }

    public static CashAccount cash(UserId owner) {
        return CashAccount.open(AccountId.newId(), owner, "Billetera", cop("50000"), NOW);
    }

    public static DepositAccount savings(UserId owner, String name) {
        return DepositAccount.open(AccountId.newId(), owner, AccountKind.SAVINGS, name,
                new Institution("Bancolombia"), new LastFourDigits("1234"), cop("1000000"), NOW);
    }

    public static CreditCardAccount creditCard(UserId owner) {
        return CreditCardAccount.open(AccountId.newId(), owner, "Mastercard Bancolombia",
                new Institution("Bancolombia"), new LastFourDigits("5403"), cop("5000000"), cop("0"), NOW);
    }

    public static OtherAccount other(UserId owner) {
        return OtherAccount.createFor(AccountId.newId(), owner, SupportedCurrencies.COP, NOW);
    }

    public static Category category(UserId owner, CategoryKind kind, String name) {
        return Category.createTopLevel(CategoryId.newId(), owner, kind, name, NOW);
    }

    public static TransactionDetails on(LocalDate date) {
        return new TransactionDetails(date, null, null, null);
    }
}
