package com.personaltracker.finance.application.port.out;

import com.personaltracker.finance.domain.exception.CategoryNotFoundException;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Categorías del usuario. Todas las operaciones filtran por propietario (ADR-0003). */
public interface CategoryRepository {

    Optional<Category> findByIdAndOwner(CategoryId id, UserId owner);

    /** Incluye las archivadas. */
    List<Category> findAllByOwner(UserId owner);

    List<Category> findChildren(CategoryId parent, UserId owner);

    boolean existsByOwner(UserId owner);

    /**
     * Indica si ya hay una categoría con ese nombre (sin distinguir mayúsculas) entre las hermanas del
     * mismo tipo. {@code parent} es nulo para las categorías principales; {@code excluding} puede ser nulo.
     */
    boolean existsSiblingNamed(UserId owner, CategoryKind kind, CategoryId parent, String name, CategoryId excluding);

    void save(Category category);

    void delete(Category category);

    /** Una categoría ajena se trata igual que una inexistente: 404. */
    default Category require(CategoryId id, UserId owner) {
        return findByIdAndOwner(id, owner).orElseThrow(CategoryNotFoundException::new);
    }

    /** Para categorías opcionales: devuelve nulo si {@code id} es nulo. */
    default Category requireIfPresent(CategoryId id, UserId owner) {
        return id == null ? null : require(id, owner);
    }
}
