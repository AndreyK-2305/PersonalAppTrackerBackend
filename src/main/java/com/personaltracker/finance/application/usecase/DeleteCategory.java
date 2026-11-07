package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.exception.CategoryInUseException;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Borra una categoría sin movimientos ni subcategorías; si no, hay que archivarla (ADR-0014). */
@Service
public class DeleteCategory {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public DeleteCategory(CategoryRepository categories, TransactionRepository transactions) {
        this.categories = categories;
        this.transactions = transactions;
    }

    @Transactional
    public void execute(UserId owner, CategoryId id) {
        Category category = categories.require(id, owner);
        if (!categories.findChildren(category.id(), owner).isEmpty()
                || transactions.existsByAnyCategory(Set.of(category.id()), owner)) {
            throw new CategoryInUseException();
        }
        categories.delete(category);
    }
}
