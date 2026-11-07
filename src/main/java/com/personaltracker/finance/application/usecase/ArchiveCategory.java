package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archiva una categoría; si es principal, también sus subcategorías (ADR-0014). */
@Service
public class ArchiveCategory {

    private final CategoryRepository categories;
    private final Clock clock;

    public ArchiveCategory(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public void execute(UserId owner, CategoryId id) {
        Category category = categories.require(id, owner);
        Instant now = clock.instant();
        category.archive(now);
        categories.save(category);
        if (category.isTopLevel()) {
            for (Category child : categories.findChildren(category.id(), owner)) {
                child.archive(now);
                categories.save(child);
            }
        }
    }
}
