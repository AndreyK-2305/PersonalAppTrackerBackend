package com.personaltracker.finance.infrastructure.persistence;

import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.CashAccount;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.Institution;
import com.personaltracker.finance.domain.model.LastFourDigits;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link AccountRepository} con JPA. Traduce la jerarquía sellada {@code Account} a una
 * fila con {@code kind} como discriminador; las columnas que no aplican al tipo quedan nulas. Al
 * guardar copia el estado sobre la entidad gestionada para aprovechar el bloqueo optimista.
 */
@Component
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository repository;

    AccountPersistenceAdapter(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findByIdAndOwner(AccountId id, UserId owner) {
        return repository.findByIdAndUserId(id.value(), owner.value()).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public List<Account> findAllByOwner(UserId owner) {
        return repository.findByUserIdOrderByCreatedAt(owner.value()).stream()
                .map(AccountPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<OtherAccount> findOtherAccount(UserId owner) {
        return repository.findByUserIdAndKind(owner.value(), AccountKind.OTHER.name())
                .map(entity -> (OtherAccount) toDomain(entity));
    }

    /** Compara en minúsculas, igual que el índice único {@code accounts_user_name_key}. */
    @Override
    public boolean existsByOwnerAndName(UserId owner, String name, AccountId excluding) {
        String wanted = name.toLowerCase(Locale.ROOT);
        UUID excludedId = excluding == null ? null : excluding.value();
        return repository.findByUserIdOrderByCreatedAt(owner.value()).stream()
                .anyMatch(entity -> !entity.getId().equals(excludedId)
                        && entity.getName().toLowerCase(Locale.ROOT).equals(wanted));
    }

    @Override
    public void save(Account account) {
        AccountJpaEntity entity = repository.findById(account.id().value())
                .orElseGet(() -> new AccountJpaEntity(account.id().value()));
        entity.setUserId(account.owner().value());
        entity.setKind(account.kind().name());
        entity.setName(account.name());
        entity.setCurrency(account.currency().getCurrencyCode());
        entity.setOpeningBalance(account.openingBalance().amount());
        entity.setArchivedAt(account.archivedAt());
        entity.setCreatedAt(account.createdAt());
        entity.setUpdatedAt(account.updatedAt());
        switch (account) {
            case CashAccount cash -> setIssuer(entity, null, null, null);
            case DepositAccount deposit -> setIssuer(entity, deposit.institution(), deposit.lastFourDigits(), null);
            case CreditCardAccount card ->
                    setIssuer(entity, card.institution(), card.lastFourDigits(), card.creditLimit());
            case OtherAccount other -> setIssuer(entity, null, null, null);
        }
        repository.save(entity);
    }

    @Override
    public void delete(Account account) {
        repository.deleteById(account.id().value());
    }

    private static void setIssuer(
            AccountJpaEntity entity, Institution institution, LastFourDigits lastFourDigits, Money creditLimit) {
        entity.setInstitution(institution == null ? null : institution.name());
        entity.setLastFourDigits(lastFourDigits == null ? null : lastFourDigits.value());
        entity.setCreditLimit(creditLimit == null ? null : creditLimit.amount());
    }

    private static Account toDomain(AccountJpaEntity entity) {
        AccountId id = new AccountId(entity.getId());
        UserId owner = new UserId(entity.getUserId());
        Currency currency = Currency.getInstance(entity.getCurrency());
        Money openingBalance = new Money(entity.getOpeningBalance(), currency);
        AccountKind kind = AccountKind.valueOf(entity.getKind());
        return switch (kind) {
            case CASH -> CashAccount.restore(id, owner, entity.getName(), currency, openingBalance,
                    entity.getArchivedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
            case SAVINGS, CHECKING, DIGITAL_WALLET -> DepositAccount.restore(id, owner, kind, entity.getName(),
                    new Institution(entity.getInstitution()), lastFourDigits(entity), currency, openingBalance,
                    entity.getArchivedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
            case CREDIT_CARD -> CreditCardAccount.restore(id, owner, entity.getName(),
                    new Institution(entity.getInstitution()), lastFourDigits(entity),
                    new Money(entity.getCreditLimit(), currency), currency, openingBalance, entity.getArchivedAt(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
            case OTHER -> OtherAccount.restore(id, owner, currency, entity.getCreatedAt(), entity.getUpdatedAt());
        };
    }

    private static LastFourDigits lastFourDigits(AccountJpaEntity entity) {
        return entity.getLastFourDigits() == null ? null : new LastFourDigits(entity.getLastFourDigits());
    }
}
