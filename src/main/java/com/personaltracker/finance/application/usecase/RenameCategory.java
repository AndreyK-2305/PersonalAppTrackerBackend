package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.RenameCategoryCommand;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.exception.CategoryNameTakenException;
import com.personaltracker.finance.domain.model.Category;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cambia el nombre de una categoría; el tipo y la categoría principal no cambian. */
@Service
public class RenameCategory {

    private final CategoryRepository categories;
    private final Clock clock;

    public RenameCategory(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public void execute(RenameCategoryCommand command) {
        Category category = categories.require(command.category(), command.owner());
        category.rename(command.name(), clock.instant());
        if (categories.existsSiblingNamed(
                command.owner(), category.kind(), category.parentId(), category.name(), category.id())) {
            throw new CategoryNameTakenException();
        }
        categories.save(category);
    }
}
