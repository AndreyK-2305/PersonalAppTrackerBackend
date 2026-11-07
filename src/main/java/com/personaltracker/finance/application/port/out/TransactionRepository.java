package com.personaltracker.finance.application.port.out;

import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.TransactionSearch;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.domain.exception.TransactionNotFoundException;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.shared.domain.UserId;
import java.util.Optional;
import java.util.Set;

/** Movimientos del usuario. Todas las operaciones filtran por propietario (ADR-0003). */
public interface TransactionRepository {

    Optional<Transaction> findByIdAndOwner(TransactionId id, UserId owner);

    PageResult<Transaction> search(UserId owner, TransactionSearch search, TransactionSort sort, PageQuery page);

    /** Si algún movimiento usa el producto como origen, destino o producto ajustado. */
    boolean existsByAccount(AccountId account, UserId owner);

    boolean existsByAnyCategory(Set<CategoryId> categories, UserId owner);

    void save(Transaction transaction);

    void delete(Transaction transaction);

    /** Un movimiento ajeno se trata igual que uno inexistente: 404. */
    default Transaction require(TransactionId id, UserId owner) {
        return findByIdAndOwner(id, owner).orElseThrow(TransactionNotFoundException::new);
    }
}
