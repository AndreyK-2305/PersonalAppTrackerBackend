package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.LATER;
import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.domain.exception.CategoryArchivedException;
import com.personaltracker.finance.domain.exception.CategoryDepthExceededException;
import com.personaltracker.finance.domain.exception.CategoryKindMismatchException;
import com.personaltracker.finance.domain.exception.InvalidCategoryNameException;
import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.Test;

class CategoryTests {

    private final UserId owner = UserId.newId();
    private final Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");

    @Test
    void childInheritsOwnerAndKindFromItsParent() {
        Category groceries = Category.createChild(CategoryId.newId(), food, " Mercado ", NOW);

        assertThat(groceries.owner()).isEqualTo(owner);
        assertThat(groceries.kind()).isEqualTo(CategoryKind.EXPENSE);
        assertThat(groceries.parentId()).isEqualTo(food.id());
        assertThat(groceries.name()).isEqualTo("Mercado");
        assertThat(groceries.isTopLevel()).isFalse();
        assertThat(food.isTopLevel()).isTrue();
    }

    @Test
    void allowsOnlyTwoLevels() {
        Category groceries = Category.createChild(CategoryId.newId(), food, "Mercado", NOW);

        assertThatThrownBy(() -> Category.createChild(CategoryId.newId(), groceries, "Frutas", NOW))
                .isInstanceOf(CategoryDepthExceededException.class)
                .extracting("code").isEqualTo("finance.category.depth_exceeded");
    }

    @Test
    void archivedParentsDoNotAcceptNewChildren() {
        food.archive(LATER);

        assertThatThrownBy(() -> Category.createChild(CategoryId.newId(), food, "Mercado", NOW))
                .isInstanceOf(CategoryArchivedException.class);
    }

    @Test
    void validatesTheName() {
        assertThatThrownBy(() -> FinanceTestData.category(owner, CategoryKind.INCOME, "  "))
                .isInstanceOf(InvalidCategoryNameException.class);
        assertThatThrownBy(() -> food.rename("x".repeat(51), LATER))
                .isInstanceOf(InvalidCategoryNameException.class);

        food.rename("Comida", LATER);

        assertThat(food.name()).isEqualTo("Comida");
        assertThat(food.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void isUsableOnlyForItsKindWhileActive() {
        assertThatCode(() -> food.ensureUsableFor(CategoryKind.EXPENSE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> food.ensureUsableFor(CategoryKind.INCOME))
                .isInstanceOf(CategoryKindMismatchException.class);

        food.archive(LATER);

        assertThatThrownBy(() -> food.ensureUsableFor(CategoryKind.EXPENSE))
                .isInstanceOf(CategoryArchivedException.class);

        food.reactivate(LATER);

        assertThat(food.isArchived()).isFalse();
    }
}
