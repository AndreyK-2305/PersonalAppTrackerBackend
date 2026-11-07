package com.personaltracker.finance.domain.model;

import static com.personaltracker.finance.domain.model.FinanceTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DefaultCategoryCatalogTests {

    private final List<DefaultCategoryCatalog.Entry> entries = DefaultCategoryCatalog.entries();

    @Test
    void everyEntryBuildsValidCategories() {
        UserId owner = UserId.newId();

        assertThatCode(() -> entries.forEach(entry -> {
            Category parent = Category.createTopLevel(CategoryId.newId(), owner, entry.kind(), entry.name(), NOW);
            entry.subcategories().forEach(name -> Category.createChild(CategoryId.newId(), parent, name, NOW));
        })).doesNotThrowAnyException();
    }

    @Test
    void namesAreUniqueAmongSiblings() {
        for (CategoryKind kind : CategoryKind.values()) {
            List<String> topLevel = entries.stream()
                    .filter(entry -> entry.kind() == kind)
                    .map(entry -> entry.name().toLowerCase(Locale.ROOT))
                    .toList();
            assertThat(topLevel).doesNotHaveDuplicates();
        }
        entries.forEach(entry -> assertThat(entry.subcategories()).doesNotHaveDuplicates());
    }

    @Test
    void includesTheCategoriesAgreedWithTheUser() {
        assertThat(entries).extracting(DefaultCategoryCatalog.Entry::name)
                .contains("Vivienda", "Alimentación", "Servicios financieros", "Salario", "Ventas", "Otros gastos",
                        "Otros ingresos");
        assertThat(entries).filteredOn(entry -> entry.name().equals("Servicios financieros"))
                .singleElement()
                .extracting(DefaultCategoryCatalog.Entry::subcategories)
                .isEqualTo(List.of("4x1000", "Cuota de manejo", "Intereses", "Comisiones"));
    }
}
