package com.personaltracker.finance.infrastructure.persistence;

import com.personaltracker.finance.application.port.out.CategoryRepository;
import com.personaltracker.finance.domain.model.Category;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementa {@link CategoryRepository} con JPA. */
@Component
class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository repository;

    CategoryPersistenceAdapter(CategoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Category> findByIdAndOwner(CategoryId id, UserId owner) {
        return repository.findByIdAndUserId(id.value(), owner.value()).map(CategoryPersistenceAdapter::toDomain);
    }

    @Override
    public List<Category> findAllByOwner(UserId owner) {
        return repository.findByUserIdOrderByCreatedAt(owner.value()).stream()
                .map(CategoryPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Category> findChildren(CategoryId parent, UserId owner) {
        return repository.findByParentIdAndUserIdOrderByCreatedAt(parent.value(), owner.value()).stream()
                .map(CategoryPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOwner(UserId owner) {
        return repository.existsByUserId(owner.value());
    }

    /** Compara en minúsculas, igual que el índice único {@code categories_sibling_name_key}. */
    @Override
    public boolean existsSiblingNamed(
            UserId owner, CategoryKind kind, CategoryId parent, String name, CategoryId excluding) {
        String wanted = name.toLowerCase(Locale.ROOT);
        UUID parentId = parent == null ? null : parent.value();
        UUID excludedId = excluding == null ? null : excluding.value();
        return repository.findByUserIdAndKind(owner.value(), kind.name()).stream()
                .anyMatch(entity -> Objects.equals(entity.getParentId(), parentId)
                        && !entity.getId().equals(excludedId)
                        && entity.getName().toLowerCase(Locale.ROOT).equals(wanted));
    }

    @Override
    public void save(Category category) {
        CategoryJpaEntity entity = repository.findById(category.id().value())
                .orElseGet(() -> new CategoryJpaEntity(category.id().value()));
        entity.setUserId(category.owner().value());
        entity.setKind(category.kind().name());
        entity.setName(category.name());
        entity.setParentId(category.parentId() == null ? null : category.parentId().value());
        entity.setArchivedAt(category.archivedAt());
        entity.setCreatedAt(category.createdAt());
        entity.setUpdatedAt(category.updatedAt());
        repository.save(entity);
    }

    @Override
    public void delete(Category category) {
        repository.deleteById(category.id().value());
    }

    private static Category toDomain(CategoryJpaEntity entity) {
        return Category.restore(
                new CategoryId(entity.getId()),
                new UserId(entity.getUserId()),
                CategoryKind.valueOf(entity.getKind()),
                entity.getName(),
                entity.getParentId() == null ? null : new CategoryId(entity.getParentId()),
                entity.getArchivedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
