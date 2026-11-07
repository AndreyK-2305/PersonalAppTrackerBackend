package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.exception.CategoryArchivedException;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reactiva una categoría. Reactivar una principal también reactiva sus subcategorías; una subcategoría
 * no se puede reactivar mientras su principal siga archivada.
 */
@Service
public class ReactivateCategory {

    private final CategoryRepository categories;
    private final Clock clock;

    public ReactivateCategory(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public void execute(UserId owner, CategoryId id) {
        Category category = categories.require(id, owner);
        if (!category.isTopLevel() && categories.require(category.parentId(), owner).isArchived()) {
            throw new CategoryArchivedException();
        }
        Instant now = clock.instant();
        category.reactivate(now);
        categories.save(category);
        if (category.isTopLevel()) {
            for (Category child : categories.findChildren(category.id(), owner)) {
                child.reactivate(now);
                categories.save(child);
            }
        }
    }
}
