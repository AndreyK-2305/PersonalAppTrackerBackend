package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.ListTransactionsQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.TransactionSearch;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.dto.TransactionView;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.exception.InvalidDateRangeException;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista los movimientos con filtros y paginación. Filtrar por una categoría principal incluye sus
 * subcategorías; filtrar por un producto incluye los movimientos donde es origen o destino. Un
 * producto o una categoría ajenos en el filtro responden 404, igual que en el resto de operaciones.
 */
@Service
public class ListTransactions {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    public ListTransactions(
            TransactionRepository transactions, AccountRepository accounts, CategoryRepository categories) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PageResult<TransactionView> execute(ListTransactionsQuery query) {
        UserId owner = query.owner();
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidDateRangeException();
        }
        if (query.account() != null) {
            accounts.require(query.account(), owner);
        }
        TransactionSearch search = new TransactionSearch(query.from(), query.to(), query.account(),
                categoryWithChildren(query.category(), owner), query.type(), normalized(query.text()),
                query.uncategorized());
        TransactionSort sort = query.sort() == null ? TransactionSort.DATE_DESC : query.sort();
        return transactions.search(owner, search, sort, query.page()).map(TransactionView::of);
    }

    private Set<CategoryId> categoryWithChildren(CategoryId id, UserId owner) {
        if (id == null) {
            return null;
        }
        Category category = categories.require(id, owner);
        Set<CategoryId> ids = new HashSet<>();
        ids.add(category.id());
        if (category.isTopLevel()) {
            categories.findChildren(category.id(), owner).forEach(child -> ids.add(child.id()));
        }
        return ids;
    }

    private static String normalized(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
