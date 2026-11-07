package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categorías del usuario como árbol de dos niveles, incluidas las archivadas. Orden: por tipo
 * (ingresos primero) y por nombre.
 */
@Service
public class ListCategories {

    private static final Comparator<Category> ORDER = Comparator
            .comparing(Category::kind)
            .thenComparing(category -> category.name().toLowerCase(Locale.ROOT));

    private final CategoryRepository categories;

    public ListCategories(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> execute(UserId owner) {
        List<Category> all = categories.findAllByOwner(owner).stream().sorted(ORDER).toList();
        Map<CategoryId, List<Category>> childrenByParent = all.stream()
                .filter(category -> !category.isTopLevel())
                .collect(Collectors.groupingBy(Category::parentId));
        return all.stream()
                .filter(Category::isTopLevel)
                .map(parent -> CategoryView.of(parent, childrenByParent.getOrDefault(parent.id(), List.of()).stream()
                        .map(child -> CategoryView.of(child, List.of()))
                        .toList()))
                .toList();
    }
}
