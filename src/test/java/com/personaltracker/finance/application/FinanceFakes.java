package com.personaltracker.finance.application;

import com.personaltracker.finance.application.dto.DailyTotal;
import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.PageResult;
import com.personaltracker.finance.application.dto.TransactionSearch;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.Account;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.BalanceEffect;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.Income;
import com.personaltracker.finance.domain.model.OtherAccount;
import com.personaltracker.finance.domain.model.ReportEffect;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dobles en memoria de los puertos de finance para probar los casos de uso sin Spring. Las sumas se
 * calculan con {@code reportEffect()} y {@code balanceEffects()} del dominio: son la referencia que
 * la consulta SQL debe reproducir (ADR-0013).
 */
public final class FinanceFakes {

    public static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FinanceFakes() {
    }

    public static final class InMemoryAccountRepository implements AccountRepository {

        private final Map<AccountId, Account> accounts = new LinkedHashMap<>();

        @Override
        public Optional<Account> findByIdAndOwner(AccountId id, UserId owner) {
            return Optional.ofNullable(accounts.get(id)).filter(account -> account.owner().equals(owner));
        }

        @Override
        public List<Account> findAllByOwner(UserId owner) {
            return accounts.values().stream().filter(account -> account.owner().equals(owner)).toList();
        }

        @Override
        public Optional<OtherAccount> findOtherAccount(UserId owner) {
            return findAllByOwner(owner).stream()
                    .filter(OtherAccount.class::isInstance)
                    .map(OtherAccount.class::cast)
                    .findFirst();
        }

        @Override
        public boolean existsByOwnerAndName(UserId owner, String name, AccountId excluding) {
            return findAllByOwner(owner).stream()
                    .anyMatch(account -> !account.id().equals(excluding) && account.name().equalsIgnoreCase(name));
        }

        @Override
        public void save(Account account) {
            accounts.put(account.id(), account);
        }

        @Override
        public void delete(Account account) {
            accounts.remove(account.id());
        }

        public int count() {
            return accounts.size();
        }
    }

    public static final class InMemoryCategoryRepository implements CategoryRepository {

        private final Map<CategoryId, Category> categories = new LinkedHashMap<>();

        @Override
        public Optional<Category> findByIdAndOwner(CategoryId id, UserId owner) {
            return Optional.ofNullable(categories.get(id)).filter(category -> category.owner().equals(owner));
        }

        @Override
        public List<Category> findAllByOwner(UserId owner) {
            return categories.values().stream().filter(category -> category.owner().equals(owner)).toList();
        }

        @Override
        public List<Category> findChildren(CategoryId parent, UserId owner) {
            return findAllByOwner(owner).stream().filter(category -> parent.equals(category.parentId())).toList();
        }

        @Override
        public boolean existsByOwner(UserId owner) {
            return !findAllByOwner(owner).isEmpty();
        }

        @Override
        public boolean existsSiblingNamed(
                UserId owner, CategoryKind kind, CategoryId parent, String name, CategoryId excluding) {
            return findAllByOwner(owner).stream().anyMatch(category -> category.kind() == kind
                    && Objects.equals(category.parentId(), parent)
                    && !category.id().equals(excluding)
                    && category.name().equalsIgnoreCase(name));
        }

        @Override
        public void save(Category category) {
            categories.put(category.id(), category);
        }

        @Override
        public void delete(Category category) {
            categories.remove(category.id());
        }

        public Optional<Category> findByName(UserId owner, String name) {
            return findAllByOwner(owner).stream().filter(category -> category.name().equals(name)).findFirst();
        }
    }

    public static final class InMemoryTransactionRepository implements TransactionRepository, TransactionTotals {

        private final Map<TransactionId, Transaction> transactions = new LinkedHashMap<>();

        @Override
        public Optional<Transaction> findByIdAndOwner(TransactionId id, UserId owner) {
            return Optional.ofNullable(transactions.get(id)).filter(transaction -> transaction.owner().equals(owner));
        }

        @Override
        public PageResult<Transaction> search(
                UserId owner, TransactionSearch search, TransactionSort sort, PageQuery page) {
            List<Transaction> matching = ownedBy(owner).stream()
                    .filter(matches(search))
                    .sorted(comparator(sort))
                    .toList();
            int from = Math.min(page.page() * page.size(), matching.size());
            int to = Math.min(from + page.size(), matching.size());
            return new PageResult<>(matching.subList(from, to), page.page(), page.size(), matching.size());
        }

        @Override
        public boolean existsByAccount(AccountId account, UserId owner) {
            return ownedBy(owner).stream().anyMatch(transaction -> touches(transaction, account));
        }

        @Override
        public boolean existsByAnyCategory(Set<CategoryId> categories, UserId owner) {
            return ownedBy(owner).stream().anyMatch(transaction -> hasCategoryIn(transaction, categories));
        }

        @Override
        public void save(Transaction transaction) {
            transactions.put(transaction.id(), transaction);
        }

        @Override
        public void delete(Transaction transaction) {
            transactions.remove(transaction.id());
        }

        @Override
        public List<DailyTotal> dailyTotals(UserId owner, DateRange range) {
            Map<TotalKey, Money> grouped = new LinkedHashMap<>();
            for (Transaction transaction : ownedBy(owner)) {
                LocalDate date = transaction.details().date();
                if (transaction.reportEffect() == ReportEffect.NEUTRAL || !range.contains(date)) {
                    continue;
                }
                // Un ingreso o un gasto afecta un solo producto.
                AccountId account = transaction.balanceEffects().getFirst().account();
                TotalKey key = new TotalKey(date, account, categoryOf(transaction), transaction.reportEffect());
                grouped.merge(key, transaction.amount(), Money::plus);
            }
            return grouped.entrySet().stream()
                    .map(entry -> new DailyTotal(entry.getKey().date(), entry.getKey().account(),
                            entry.getKey().category(), entry.getKey().effect(), entry.getValue()))
                    .toList();
        }

        @Override
        public Map<AccountId, Money> balanceChanges(UserId owner) {
            Map<AccountId, Money> changes = new HashMap<>();
            for (Transaction transaction : ownedBy(owner)) {
                for (BalanceEffect effect : transaction.balanceEffects()) {
                    changes.merge(effect.account(), effect.change(), Money::plus);
                }
            }
            return changes;
        }

        public int count() {
            return transactions.size();
        }

        private List<Transaction> ownedBy(UserId owner) {
            return transactions.values().stream().filter(transaction -> transaction.owner().equals(owner)).toList();
        }

        private static Predicate<Transaction> matches(TransactionSearch search) {
            return transaction -> {
                TransactionDetails details = transaction.details();
                return (search.from() == null || !details.date().isBefore(search.from()))
                        && (search.to() == null || !details.date().isAfter(search.to()))
                        && (search.account() == null || touches(transaction, search.account()))
                        && (search.categories() == null || hasCategoryIn(transaction, search.categories()))
                        && (search.type() == null || transaction.type() == search.type())
                        && (search.text() == null || contains(details.description(), search.text())
                                || contains(details.merchant(), search.text()));
            };
        }

        private static Comparator<Transaction> comparator(TransactionSort sort) {
            Comparator<Transaction> byDate = Comparator.comparing(transaction -> transaction.details().date());
            Comparator<Transaction> byAmount = Comparator.comparing(transaction -> transaction.amount().amount());
            Comparator<Transaction> primary = switch (sort) {
                case DATE_DESC -> byDate.reversed();
                case DATE_ASC -> byDate;
                case AMOUNT_DESC -> byAmount.reversed();
                case AMOUNT_ASC -> byAmount;
            };
            return primary.thenComparing(Transaction::createdAt, Comparator.reverseOrder());
        }

        private static boolean touches(Transaction transaction, AccountId account) {
            return transaction.balanceEffects().stream().anyMatch(effect -> effect.account().equals(account));
        }

        private static CategoryId categoryOf(Transaction transaction) {
            return switch (transaction) {
                case Income income -> income.category();
                case Expense expense -> expense.category();
                default -> null;
            };
        }

        /** Los conjuntos inmutables no admiten {@code contains(null)}: un movimiento sin categoría no coincide. */
        private static boolean hasCategoryIn(Transaction transaction, Set<CategoryId> categories) {
            CategoryId category = categoryOf(transaction);
            return category != null && categories.contains(category);
        }

        private static boolean contains(String value, String text) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
        }

        private record TotalKey(LocalDate date, AccountId account, CategoryId category, ReportEffect effect) {}
    }
}
