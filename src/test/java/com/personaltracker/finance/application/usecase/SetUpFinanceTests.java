package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.FinanceFakes.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.finance.application.FinanceFakes.InMemoryAccountRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryCategoryRepository;
import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.DefaultCategoryCatalog;
import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.Test;

class SetUpFinanceTests {

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final SetUpFinance setUp = new SetUpFinance(accounts, categories, CLOCK);
    private final GetFinanceStatus status = new GetFinanceStatus(accounts);
    private final UserId owner = UserId.newId();

    @Test
    void createsTheOtherAccountAndTheSuggestedCatalog() {
        assertThat(status.execute(owner).setUp()).isFalse();

        assertThat(setUp.execute(new SetUpFinanceCommand(owner, true)).setUp()).isTrue();

        assertThat(status.execute(owner).setUp()).isTrue();
        assertThat(accounts.findOtherAccount(owner)).isPresent();
        int expected = DefaultCategoryCatalog.entries().stream().mapToInt(entry -> 1 + entry.subcategories().size()).sum();
        assertThat(categories.findAllByOwner(owner)).hasSize(expected);
        Category fourPerThousand = categories.findByName(owner, "4x1000").orElseThrow();
        assertThat(categories.findByIdAndOwner(fourPerThousand.parentId(), owner).orElseThrow().name())
                .isEqualTo("Servicios financieros");
    }

    @Test
    void canSkipTheSuggestedCatalog() {
        setUp.execute(new SetUpFinanceCommand(owner, false));

        assertThat(accounts.findOtherAccount(owner)).isPresent();
        assertThat(categories.existsByOwner(owner)).isFalse();
    }

    @Test
    void isIdempotent() {
        setUp.execute(new SetUpFinanceCommand(owner, true));
        int categoriesAfterFirstRun = categories.findAllByOwner(owner).size();

        setUp.execute(new SetUpFinanceCommand(owner, true));

        assertThat(accounts.count()).isEqualTo(1);
        assertThat(categories.findAllByOwner(owner)).hasSize(categoriesAfterFirstRun);
    }

    @Test
    void doesNotAddTheCatalogWhenTheUserAlreadyHasCategories() {
        new CreateCategory(categories, CLOCK).execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, "Mascotas", null));

        setUp.execute(new SetUpFinanceCommand(owner, true));

        assertThat(categories.findAllByOwner(owner)).extracting(Category::name).containsExactly("Mascotas");
    }

    @Test
    void eachUserGetsTheirOwnSetup() {
        UserId other = UserId.newId();
        setUp.execute(new SetUpFinanceCommand(owner, true));

        assertThat(status.execute(other).setUp()).isFalse();
        assertThat(categories.existsByOwner(other)).isFalse();
    }
}
