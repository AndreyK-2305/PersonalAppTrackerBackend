package com.personaltracker.finance.application.port.out;

import com.personaltracker.finance.domain.exception.AccountNotFoundException;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Productos del usuario. Todas las operaciones filtran por propietario (ADR-0003). */
public interface AccountRepository {

    Optional<Account> findByIdAndOwner(AccountId id, UserId owner);

    /** Incluye los archivados y el producto "Otro". */
    List<Account> findAllByOwner(UserId owner);

    Optional<OtherAccount> findOtherAccount(UserId owner);

    /** Compara sin distinguir mayúsculas; {@code excluding} puede ser nulo. */
    boolean existsByOwnerAndName(UserId owner, String name, AccountId excluding);

    void save(Account account);

    void delete(Account account);

    /** Un producto ajeno se trata igual que uno inexistente: 404. */
    default Account require(AccountId id, UserId owner) {
        return findByIdAndOwner(id, owner).orElseThrow(AccountNotFoundException::new);
    }
}
