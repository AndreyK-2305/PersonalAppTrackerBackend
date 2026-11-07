package com.personaltracker.finance.infrastructure.persistence;

import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.FinanceTestData;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CategoryPersistenceAdapterTests {

    @Autowired
    CategoryRepository categories;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    private UserId owner;

    @BeforeEach
    void createOwner() {
        owner = newUser();
    }

    @Test
    void savesAndRestoresParentsAndChildren() {
        Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        Category groceries = Category.createChild(CategoryId.newId(), food, "Mercado", NOW);
        groceries.archive(NOW);
        categories.save(food);
        categories.save(groceries);

        assertThat(categories.findByIdAndOwner(groceries.id(), owner).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(groceries);
        assertThat(categories.findChildren(food.id(), owner)).extracting(Category::id).containsExactly(groceries.id());
        assertThat(categories.findAllByOwner(owner)).hasSize(2);
        assertThat(categories.existsByOwner(owner)).isTrue();
    }

    @Test
    void findsSiblingsByNameIgnoringCase() {
        Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        Category groceries = Category.createChild(CategoryId.newId(), food, "Mercado", NOW);
        categories.save(food);
        categories.save(groceries);

        assertThat(categories.existsSiblingNamed(owner, CategoryKind.EXPENSE, null, "ALIMENTACIÓN", null)).isTrue();
        assertThat(categories.existsSiblingNamed(owner, CategoryKind.EXPENSE, null, "Alimentación", food.id())).isFalse();
        assertThat(categories.existsSiblingNamed(owner, CategoryKind.INCOME, null, "Alimentación", null)).isFalse();
        assertThat(categories.existsSiblingNamed(owner, CategoryKind.EXPENSE, food.id(), "mercado", null)).isTrue();
        assertThat(categories.existsSiblingNamed(owner, CategoryKind.EXPENSE, null, "Mercado", null)).isFalse();
    }

    @Test
    void neverReturnsAnotherUsersCategories() {
        Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        categories.save(food);
        UserId intruder = newUser();

        assertThat(categories.findByIdAndOwner(food.id(), intruder)).isEmpty();
        assertThat(categories.findChildren(food.id(), intruder)).isEmpty();
        assertThat(categories.existsByOwner(intruder)).isFalse();
    }

    @Test
    void theDatabaseRejectsDuplicateSiblingNames() {
        categories.save(FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación"));

        assertThatThrownBy(() -> categories.save(FinanceTestData.category(owner, CategoryKind.EXPENSE, "alimentación")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAParentOfAnotherUserOrOfAnotherKind() {
        Category food = FinanceTestData.category(owner, CategoryKind.EXPENSE, "Alimentación");
        categories.save(food);
        UserId intruder = newUser();

        assertThatThrownBy(() -> insertChild(intruder, "EXPENSE", food.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChild(owner, "INCOME", food.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertChild(UserId user, String kind, CategoryId parent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                insert into finance.categories (id, user_id, kind, name, parent_id, created_at, updated_at, version)
                values (?, ?, ?, 'Hija', ?, ?, ?, 0)
                """, UUID.randomUUID(), user.value(), kind, parent.value(), now, now);
    }

    private UserId newUser() {
        return new TestAccounts(users, passwordHasher).create().id();
    }
}
