package com.personaltracker.goals.infrastructure.persistence;

import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Implementa {@link FinancedGoalRepository} con JPA sobre {@code goals.financed_goals}. */
@Component
class FinancedGoalPersistenceAdapter implements FinancedGoalRepository {

    private final FinancedGoalJpaRepository repository;

    FinancedGoalPersistenceAdapter(FinancedGoalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<FinancedGoal> findByIdAndOwner(FinancedGoalId id, UserId owner) {
        return repository.findByIdAndUserId(id.value(), owner.value()).map(FinancedGoalPersistenceAdapter::toDomain);
    }

    @Override
    public List<FinancedGoal> findAllByOwner(UserId owner) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(owner.value()).stream()
                .map(FinancedGoalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(FinancedGoal goal) {
        FinancedTerms terms = goal.terms();
        FinancedGoalJpaEntity entity = repository.findById(goal.id().value())
                .orElseGet(() -> new FinancedGoalJpaEntity(goal.id().value()));
        entity.setUserId(goal.owner().value());
        entity.setName(goal.name());
        entity.setAssetValue(terms.assetValue().amount());
        entity.setDownPayment(terms.downPayment().amount());
        entity.setCurrency(terms.assetValue().currency().getCurrencyCode());
        entity.setAnnualRate(terms.rate().value());
        entity.setInstallments(terms.installments());
        entity.setFirstDueDate(terms.firstDueDate());
        entity.setActualInstallment(terms.actualInstallment() == null ? null : terms.actualInstallment().amount());
        entity.setCreatedAt(goal.createdAt());
        entity.setUpdatedAt(goal.updatedAt());
        repository.save(entity);
    }

    @Override
    public void delete(FinancedGoal goal) {
        repository.deleteById(goal.id().value());
    }

    private static FinancedGoal toDomain(FinancedGoalJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        FinancedTerms terms = new FinancedTerms(
                new Money(entity.getAssetValue(), currency),
                new Money(entity.getDownPayment(), currency),
                new AnnualRate(entity.getAnnualRate()),
                entity.getInstallments(),
                entity.getFirstDueDate(),
                entity.getActualInstallment() == null ? null : new Money(entity.getActualInstallment(), currency));
        return FinancedGoal.restore(new FinancedGoalId(entity.getId()), new UserId(entity.getUserId()),
                entity.getName(), terms, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
