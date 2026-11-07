package com.personaltracker.finance.application.usecase;

import static com.personaltracker.finance.application.FinanceFakes.CLOCK;
import static com.personaltracker.finance.domain.model.FinanceTestData.cop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.FinanceFakes.InMemoryAccountRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryCategoryRepository;
import com.personaltracker.finance.application.FinanceFakes.InMemoryTransactionRepository;
import com.personaltracker.finance.application.dto.AccountDetails;
import com.personaltracker.finance.application.dto.AccountView;
import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.RenameCategoryCommand;
import com.personaltracker.finance.application.dto.TransactionDraft.ExpenseDraft;
import com.personaltracker.finance.domain.exception.CategoryArchivedException;
import com.personaltracker.finance.domain.exception.CategoryInUseException;
import com.personaltracker.finance.domain.exception.CategoryKindMismatchException;
import com.personaltracker.finance.domain.exception.CategoryNameTakenException;
import com.personaltracker.finance.domain.exception.CategoryNotFoundException;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CategoryUseCasesTests {

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
    private final CreateCategory create = new CreateCategory(categories, CLOCK);
    private final RenameCategory rename = new RenameCategory(categories, CLOCK);
    private final ArchiveCategory archive = new ArchiveCategory(categories, CLOCK);
    private final ReactivateCategory reactivate = new ReactivateCategory(categories, CLOCK);
    private final DeleteCategory delete = new DeleteCategory(categories, transactions);
    private final ListCategories list = new ListCategories(categories);
    private final UserId owner = UserId.newId();

    @Test
    void listsATwoLevelTreeWithIncomeFirst() {
        CategoryView food = expense("Alimentación", null);
        expense("Restaurantes", food.id());
        expense("Mercado", food.id());
        create.execute(new CreateCategoryCommand(owner, CategoryKind.INCOME, "Salario", null));

        assertThat(list.execute(owner)).extracting(CategoryView::name).containsExactly("Salario", "Alimentación");
        assertThat(list.execute(owner).get(1).subcategories()).extracting(CategoryView::name)
                .containsExactly("Mercado", "Restaurantes");
    }

    @Test
    void childrenMustMatchTheParentKind() {
        CategoryView food = expense("Alimentación", null);

        assertThatThrownBy(() -> create.execute(new CreateCategoryCommand(owner, CategoryKind.INCOME, "Bonos", food.id())))
                .isInstanceOf(CategoryKindMismatchException.class);
    }

    @Test
    void namesAreUniqueAmongSiblingsOfTheSameKind() {
        CategoryView food = expense("Alimentación", null);
        expense("Otros", food.id());

        assertThatThrownBy(() -> expense("alimentación", null))
                .isInstanceOf(CategoryNameTakenException.class)
                .extracting("code").isEqualTo("finance.category.name_taken");
        assertThatThrownBy(() -> expense("OTROS", food.id())).isInstanceOf(CategoryNameTakenException.class);

        expense("Otros", null);
        create.execute(new CreateCategoryCommand(owner, CategoryKind.INCOME, "Otros", null));
        assertThat(categories.findAllByOwner(owner)).hasSize(4);
    }

    @Test
    void renamesRespectingUniqueness() {
        CategoryView food = expense("Alimentación", null);
        expense("Transporte", null);

        rename.execute(new RenameCategoryCommand(owner, food.id(), "Comida"));

        assertThat(categories.require(food.id(), owner).name()).isEqualTo("Comida");
        assertThatThrownBy(() -> rename.execute(new RenameCategoryCommand(owner, food.id(), "transporte")))
                .isInstanceOf(CategoryNameTakenException.class);
    }

    @Test
    void archivingAParentArchivesItsChildrenAndReactivatingRestoresThem() {
        CategoryView food = expense("Alimentación", null);
        CategoryView groceries = expense("Mercado", food.id());

        archive.execute(owner, food.id());

        assertThat(categories.require(groceries.id(), owner).isArchived()).isTrue();
        assertThatThrownBy(() -> reactivate.execute(owner, groceries.id()))
                .isInstanceOf(CategoryArchivedException.class);

        reactivate.execute(owner, food.id());

        assertThat(categories.require(food.id(), owner).isArchived()).isFalse();
        assertThat(categories.require(groceries.id(), owner).isArchived()).isFalse();
    }

    @Test
    void deletesOnlyUnusedCategoriesWithoutChildren() {
        CategoryView food = expense("Alimentación", null);
        CategoryView groceries = expense("Mercado", food.id());
        CategoryView transport = expense("Transporte", null);
        AccountView cash = new CreateAccount(accounts, CLOCK)
                .execute(new CreateAccountCommand(owner, new AccountDetails.Cash("Billetera", cop("0"))));
        new RecordTransaction(transactions, accounts, categories, CLOCK).execute(new RecordTransactionCommand(owner,
                new ExpenseDraft(cash.id(), groceries.id(), cop("1000"),
                        new TransactionDetails(LocalDate.of(2026, 11, 6), null, null, null))));

        assertThatThrownBy(() -> delete.execute(owner, food.id())).isInstanceOf(CategoryInUseException.class);
        assertThatThrownBy(() -> delete.execute(owner, groceries.id())).isInstanceOf(CategoryInUseException.class);

        delete.execute(owner, transport.id());

        assertThat(categories.findByIdAndOwner(transport.id(), owner)).isEmpty();
    }

    @Test
    void anotherUsersCategoryIsNotFound() {
        CategoryView food = expense("Alimentación", null);
        UserId intruder = UserId.newId();

        assertThatThrownBy(() -> create.execute(new CreateCategoryCommand(intruder, CategoryKind.EXPENSE, "X", food.id())))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> rename.execute(new RenameCategoryCommand(intruder, food.id(), "X")))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> archive.execute(intruder, food.id())).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> delete.execute(intruder, food.id())).isInstanceOf(CategoryNotFoundException.class);
        assertThat(list.execute(intruder)).isEmpty();
    }

    private CategoryView expense(String name, CategoryId parent) {
        return create.execute(new CreateCategoryCommand(owner, CategoryKind.EXPENSE, name, parent));
    }
}
