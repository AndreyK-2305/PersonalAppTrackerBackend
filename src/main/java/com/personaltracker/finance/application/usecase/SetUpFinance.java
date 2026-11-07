package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.FinanceStatus;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.DefaultCategoryCatalog;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.finance.domain.model.SupportedCurrencies;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bienvenida de Finanzas (ADR-0014): crea el producto "Otro" y, si el usuario lo acepta, el catálogo
 * de categorías sugerido. Es idempotente: no duplica "Otro" y solo crea el catálogo si el usuario
 * todavía no tiene categorías.
 */
@Service
public class SetUpFinance {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Clock clock;

    public SetUpFinance(AccountRepository accounts, CategoryRepository categories, Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public FinanceStatus execute(SetUpFinanceCommand command) {
        Instant now = clock.instant();
        if (accounts.findOtherAccount(command.owner()).isEmpty()) {
            accounts.save(OtherAccount.createFor(AccountId.newId(), command.owner(), SupportedCurrencies.COP, now));
        }
        if (command.withSuggestedCategories() && !categories.existsByOwner(command.owner())) {
            for (DefaultCategoryCatalog.Entry entry : DefaultCategoryCatalog.entries()) {
                Category parent = Category.createTopLevel(
                        CategoryId.newId(), command.owner(), entry.kind(), entry.name(), now);
                categories.save(parent);
                for (String name : entry.subcategories()) {
                    categories.save(Category.createChild(CategoryId.newId(), parent, name, now));
                }
            }
        }
        return new FinanceStatus(true);
    }
}
