package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import java.util.List;

/** Categoría principal con sus subcategorías; una subcategoría tiene la lista vacía. */
public record CategoryView(
        CategoryId id, CategoryKind kind, String name, boolean archived, List<CategoryView> subcategories) {

    public CategoryView {
        subcategories = List.copyOf(subcategories);
    }

    public static CategoryView of(Category category, List<CategoryView> subcategories) {
        return new CategoryView(category.id(), category.kind(), category.name(), category.isArchived(), subcategories);
    }
}
