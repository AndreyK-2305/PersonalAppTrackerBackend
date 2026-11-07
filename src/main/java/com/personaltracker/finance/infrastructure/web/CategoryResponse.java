package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.domain.model.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Categoría principal con sus subcategorías; una subcategoría tiene la lista vacía. */
@Schema(requiredProperties = {"id", "kind", "name", "archived", "subcategories"})
record CategoryResponse(UUID id, CategoryKind kind, String name, boolean archived, List<CategoryResponse> subcategories) {

    static CategoryResponse from(CategoryView view) {
        return new CategoryResponse(view.id().value(), view.kind(), view.name(), view.archived(),
                view.subcategories().stream().map(CategoryResponse::from).toList());
    }
}
