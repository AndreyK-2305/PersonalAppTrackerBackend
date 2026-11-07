package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.dto.RenameCategoryCommand;
import com.personaltracker.finance.application.usecase.ArchiveCategory;
import com.personaltracker.finance.application.usecase.CreateCategory;
import com.personaltracker.finance.application.usecase.DeleteCategory;
import com.personaltracker.finance.application.usecase.ListCategories;
import com.personaltracker.finance.application.usecase.ReactivateCategory;
import com.personaltracker.finance.application.usecase.RenameCategory;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Categorías y subcategorías de ingresos y gastos (ADR-0014). Archivar o reactivar una categoría
 * principal aplica también a sus subcategorías. Una categoría con movimientos o subcategorías no se
 * borra (409): se archiva.
 */
@RestController
@RequestMapping("/api/v1/finance/categories")
class CategoryController {

    private final ListCategories listCategories;
    private final CreateCategory createCategory;
    private final RenameCategory renameCategory;
    private final ArchiveCategory archiveCategory;
    private final ReactivateCategory reactivateCategory;
    private final DeleteCategory deleteCategory;

    CategoryController(
            ListCategories listCategories,
            CreateCategory createCategory,
            RenameCategory renameCategory,
            ArchiveCategory archiveCategory,
            ReactivateCategory reactivateCategory,
            DeleteCategory deleteCategory) {
        this.listCategories = listCategories;
        this.createCategory = createCategory;
        this.renameCategory = renameCategory;
        this.archiveCategory = archiveCategory;
        this.reactivateCategory = reactivateCategory;
        this.deleteCategory = deleteCategory;
    }

    /** Árbol de dos niveles, incluidas las archivadas: ingresos primero y luego por nombre. */
    @GetMapping
    List<CategoryResponse> list(@CurrentUser UserId owner) {
        return listCategories.execute(owner).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@CurrentUser UserId owner, @Valid @RequestBody CreateCategoryRequest body) {
        CategoryId parent = body.parentId() == null ? null : new CategoryId(body.parentId());
        return CategoryResponse.from(
                createCategory.execute(new CreateCategoryCommand(owner, body.kind(), body.name(), parent)));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rename(@CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody RenameCategoryRequest body) {
        renameCategory.execute(new RenameCategoryCommand(owner, new CategoryId(id), body.name()));
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@CurrentUser UserId owner, @PathVariable UUID id) {
        archiveCategory.execute(owner, new CategoryId(id));
    }

    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@CurrentUser UserId owner, @PathVariable UUID id) {
        reactivateCategory.execute(owner, new CategoryId(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UserId owner, @PathVariable UUID id) {
        deleteCategory.execute(owner, new CategoryId(id));
    }

    /** {@code parentId} crea una subcategoría, que debe ser del mismo tipo que su categoría principal. */
    record CreateCategoryRequest(
            @NotNull CategoryKind kind,
            @NotBlank @Size(max = Category.MAX_NAME_LENGTH) String name,
            UUID parentId) {}

    record RenameCategoryRequest(@NotBlank @Size(max = Category.MAX_NAME_LENGTH) String name) {}
}
