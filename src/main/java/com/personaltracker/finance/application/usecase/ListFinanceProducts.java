package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.api.FinanceProducts;
import com.personaltracker.finance.api.ProductSummary;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.LastFourDigits;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publica los productos del usuario para otros módulos a través de {@code finance.api}. */
@Service
public class ListFinanceProducts implements FinanceProducts {

    private final AccountRepository accounts;
    private final SetUpFinance setUpFinance;

    public ListFinanceProducts(AccountRepository accounts, SetUpFinance setUpFinance) {
        this.accounts = accounts;
        this.setUpFinance = setUpFinance;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummary> activeProducts(UserId owner) {
        return accounts.findAllByOwner(owner).stream()
                .filter(account -> !account.isArchived())
                .map(ListFinanceProducts::summary)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Si el usuario nunca abrió Finanzas, crea "Otro" sin el catálogo de categorías (es idempotente). */
    @Override
    @Transactional
    public UUID otherProduct(UserId owner) {
        Optional<OtherAccount> other = accounts.findOtherAccount(owner);
        if (other.isEmpty()) {
            setUpFinance.execute(new SetUpFinanceCommand(owner, false));
            other = accounts.findOtherAccount(owner);
        }
        return other.orElseThrow().id().value();
    }

    private static Optional<ProductSummary> summary(Account account) {
        UUID id = account.id().value();
        return switch (account) {
            case CashAccount cash -> Optional.of(new ProductSummary(id, ProductSummary.Kind.CASH, null, null));
            case DepositAccount deposit -> Optional.of(new ProductSummary(id, ProductSummary.Kind.DEPOSIT,
                    deposit.institution().name(), digits(deposit.lastFourDigits())));
            case CreditCardAccount card -> Optional.of(new ProductSummary(id, ProductSummary.Kind.CREDIT_CARD,
                    card.institution().name(), digits(card.lastFourDigits())));
            case OtherAccount ignored -> Optional.empty();
        };
    }

    private static String digits(LastFourDigits digits) {
        return digits == null ? null : digits.value();
    }
}
