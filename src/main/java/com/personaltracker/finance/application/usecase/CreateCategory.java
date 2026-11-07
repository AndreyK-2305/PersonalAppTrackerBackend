package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.CategoryView;
import com.personaltracker.finance.application.dto.CreateCategoryCommand;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.exception.CategoryKindMismatchException;
import com.personaltracker.finance.domain.exception.CategoryNameTakenException;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea una categoría principal o, si se indica la principal, una subcategoría de su mismo tipo. El
 * nombre es único entre las hermanas del mismo tipo (ADR-0014).
 */
@Service
public class CreateCategory {

    private final CategoryRepository categories;
    private final Clock clock;

    public CreateCategory(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public CategoryView execute(CreateCategoryCommand command) {
        Category category;
        if (command.parent() == null) {
            category = Category.createTopLevel(
                    CategoryId.newId(), command.owner(), command.kind(), command.name(), clock.instant());
        } else {
            Category parent = categories.require(command.parent(), command.owner());
            if (parent.kind() != command.kind()) {
                throw new CategoryKindMismatchException();
            }
            category = Category.createChild(CategoryId.newId(), parent, command.name(), clock.instant());
        }
        if (categories.existsSiblingNamed(
                command.owner(), category.kind(), category.parentId(), category.name(), null)) {
            throw new CategoryNameTakenException();
        }
        categories.save(category);
        return CategoryView.of(category, List.of());
    }
}
