package com.personaltracker.finance.infrastructure.persistence;

import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.TransactionSearch;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.Adjustment;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link TransactionRepository} con JPA. Traduce la jerarquía sellada {@code Transaction}
 * a una fila con {@code type} como discriminador (ver V6). Los listados ordenan de forma estable:
 * a igual criterio, los movimientos registrados más recientemente van primero.
 */
@Component
class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository repository;

    TransactionPersistenceAdapter(TransactionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Transaction> findByIdAndOwner(TransactionId id, UserId owner) {
        return repository.findByIdAndUserId(id.value(), owner.value()).map(TransactionPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Transaction> search(UserId owner, TransactionSearch search, TransactionSort sort, PageQuery page) {
        Page<TransactionJpaEntity> result = repository.findAll(
                matching(owner, search), PageRequest.of(page.page(), page.size(), sortOf(sort)));
        return new PageResult<>(
                result.getContent().stream().map(TransactionPersistenceAdapter::toDomain).toList(),
                page.page(),
                page.size(),
                result.getTotalElements());
    }

    @Override
    public boolean existsByAccount(AccountId account, UserId owner) {
        return repository.existsByUserIdAndAccount(owner.value(), account.value());
    }

    @Override
    public boolean existsByAnyCategory(Set<CategoryId> categories, UserId owner) {
        return !categories.isEmpty()
                && repository.existsByUserIdAndCategoryIdIn(owner.value(), uuids(categories));
    }

    @Override
    public void save(Transaction transaction) {
        TransactionJpaEntity entity = repository.findById(transaction.id().value())
                .orElseGet(() -> new TransactionJpaEntity(transaction.id().value()));
        TransactionDetails details = transaction.details();
        entity.setUserId(transaction.owner().value());
        entity.setType(transaction.type().name());
        entity.setAmount(transaction.amount().amount());
        entity.setCurrency(transaction.amount().currency().getCurrencyCode());
        entity.setTransactionDate(details.date());
        entity.setDescription(details.description());
        entity.setMerchant(details.merchant());
        entity.setChannel(details.channel() == null ? null : details.channel().name());
        entity.setCreatedAt(transaction.createdAt());
        entity.setUpdatedAt(transaction.updatedAt());
        switch (transaction) {
            case Income income -> setLinks(entity, null, income.destination(), income.category());
            case Expense expense -> setLinks(entity, expense.source(), null, expense.category());
            case Transfer transfer -> setLinks(entity, transfer.source(), transfer.destination(), null);
            case Adjustment adjustment -> {
                if (adjustment.direction() == AdjustmentDirection.INCREASE) {
                    setLinks(entity, null, adjustment.account(), null);
                } else {
                    setLinks(entity, adjustment.account(), null, null);
                }
            }
        }
        repository.save(entity);
    }

    @Override
    public void delete(Transaction transaction) {
        repository.deleteById(transaction.id().value());
    }

    private static Specification<TransactionJpaEntity> matching(UserId owner, TransactionSearch search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), owner.value()));
            if (search.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<LocalDate>get("transactionDate"), search.from()));
            }
            if (search.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.<LocalDate>get("transactionDate"), search.to()));
            }
            if (search.account() != null) {
                UUID account = search.account().value();
                predicates.add(builder.or(
                        builder.equal(root.get("sourceAccountId"), account),
                        builder.equal(root.get("destinationAccountId"), account)));
            }
            if (search.categories() != null) {
                predicates.add(search.categories().isEmpty()
                        ? builder.disjunction()
                        : root.get("categoryId").in(uuids(search.categories())));
            }
            if (search.type() != null) {
                predicates.add(builder.equal(root.get("type"), search.type().name()));
            }
            if (search.text() != null) {
                String pattern = "%" + escapeLike(search.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.<String>get("description")), pattern, '\\'),
                        builder.like(builder.lower(root.<String>get("merchant")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort sortOf(TransactionSort sort) {
        Sort mostRecentFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Sort primary = switch (sort) {
            case DATE_DESC -> Sort.by(Sort.Order.desc("transactionDate"));
            case DATE_ASC -> Sort.by(Sort.Order.asc("transactionDate"));
            case AMOUNT_DESC -> Sort.by(Sort.Order.desc("amount"));
            case AMOUNT_ASC -> Sort.by(Sort.Order.asc("amount"));
        };
        return primary.and(mostRecentFirst);
    }

    /** El texto del usuario se busca literal: sus % y _ no actúan como comodines. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<UUID> uuids(Set<CategoryId> categories) {
        return categories.stream().map(CategoryId::value).toList();
    }

    private static void setLinks(
            TransactionJpaEntity entity, AccountId source, AccountId destination, CategoryId category) {
        entity.setSourceAccountId(source == null ? null : source.value());
        entity.setDestinationAccountId(destination == null ? null : destination.value());
        entity.setCategoryId(category == null ? null : category.value());
    }

    private static Transaction toDomain(TransactionJpaEntity entity) {
        TransactionId id = new TransactionId(entity.getId());
        UserId owner = new UserId(entity.getUserId());
        Money amount = new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency()));
        TransactionDetails details = new TransactionDetails(entity.getTransactionDate(), entity.getDescription(),
                entity.getMerchant(), entity.getChannel() == null ? null : PaymentChannel.valueOf(entity.getChannel()));
        Instant createdAt = entity.getCreatedAt();
        Instant updatedAt = entity.getUpdatedAt();
        AccountId source = accountId(entity.getSourceAccountId());
        AccountId destination = accountId(entity.getDestinationAccountId());
        CategoryId category = entity.getCategoryId() == null ? null : new CategoryId(entity.getCategoryId());
        return switch (TransactionType.valueOf(entity.getType())) {
            case INCOME -> Income.restore(id, owner, destination, category, amount, details, createdAt, updatedAt);
            case EXPENSE -> Expense.restore(id, owner, source, category, amount, details, createdAt, updatedAt);
            case TRANSFER -> Transfer.restore(id, owner, source, destination, amount, details, createdAt, updatedAt);
            case ADJUSTMENT -> destination != null
                    ? Adjustment.restore(id, owner, destination, AdjustmentDirection.INCREASE, amount, details,
                            createdAt, updatedAt)
                    : Adjustment.restore(id, owner, source, AdjustmentDirection.DECREASE, amount, details,
                            createdAt, updatedAt);
        };
    }

    private static AccountId accountId(UUID value) {
        return value == null ? null : new AccountId(value);
    }
}
