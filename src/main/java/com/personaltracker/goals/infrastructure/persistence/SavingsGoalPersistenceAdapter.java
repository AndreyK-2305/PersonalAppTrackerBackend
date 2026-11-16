package com.personaltracker.goals.infrastructure.persistence;

import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsPlanKind;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementa {@link SavingsGoalRepository} con JPA. Las claves foráneas borran abonos y tasas con la meta. */
@Component
class SavingsGoalPersistenceAdapter implements SavingsGoalRepository {

    private final SavingsGoalJpaRepository goals;
    private final SavingsContributionJpaRepository contributions;
    private final SavingsRateChangeJpaRepository rateChanges;

    SavingsGoalPersistenceAdapter(SavingsGoalJpaRepository goals,
            SavingsContributionJpaRepository contributions,
            SavingsRateChangeJpaRepository rateChanges) {
        this.goals = goals;
        this.contributions = contributions;
        this.rateChanges = rateChanges;
    }

    @Override
    public Optional<SavingsGoal> findByIdAndOwner(SavingsGoalId id, UserId owner) {
        return goals.findByIdAndUserId(id.value(), owner.value()).map(SavingsGoalPersistenceAdapter::toDomain);
    }

    @Override
    public List<SavingsGoal> findAllByOwner(UserId owner) {
        return goals.findAllByUserIdOrderByCreatedAtDesc(owner.value()).stream()
                .map(SavingsGoalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(SavingsGoal goal) {
        SavingsGoalJpaEntity entity = goals.findById(goal.id().value())
                .orElseGet(() -> new SavingsGoalJpaEntity(goal.id().value()));
        entity.setUserId(goal.owner().value());
        entity.setName(goal.name());
        entity.setTargetAmount(goal.target().amount());
        entity.setCurrency(goal.target().currency().getCurrencyCode());
        entity.setPlanKind(goal.plan().kind().name());
        switch (goal.plan()) {
            case SavingsPlan.ByTargetDate(LocalDate targetDate) -> {
                entity.setTargetDate(targetDate);
                entity.setMonthlyAmount(null);
            }
            case SavingsPlan.ByMonthlyAmount(Money monthly) -> {
                entity.setTargetDate(null);
                entity.setMonthlyAmount(monthly.amount());
            }
        }
        entity.setCreatedAt(goal.createdAt());
        entity.setUpdatedAt(goal.updatedAt());
        goals.save(entity);
    }

    @Override
    public void delete(SavingsGoal goal) {
        goals.deleteById(goal.id().value());
    }

    @Override
    public List<SavingsContribution> contributions(SavingsGoalId goalId, UserId owner) {
        return contributions
                .findAllByGoalIdAndUserIdOrderByContributionDateDescCreatedAtDesc(goalId.value(), owner.value())
                .stream()
                .map(SavingsGoalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<SavingsContribution> findContribution(SavingsGoalId goalId, UUID contributionId, UserId owner) {
        return contributions.findByIdAndGoalIdAndUserId(contributionId, goalId.value(), owner.value())
                .map(SavingsGoalPersistenceAdapter::toDomain);
    }

    @Override
    public void saveContribution(SavingsContribution contribution) {
        contributions.save(new SavingsContributionJpaEntity(contribution.id(), contribution.goalId().value(),
                contribution.owner().value(), contribution.date(), contribution.amount().amount(),
                contribution.amount().currency().getCurrencyCode(), contribution.createdAt()));
    }

    @Override
    public void deleteContribution(SavingsContribution contribution) {
        contributions.deleteById(contribution.id());
    }

    @Override
    public List<SavingsRateChange> rateChanges(SavingsGoalId goalId, UserId owner) {
        return rateChanges.findAllByGoalIdAndUserIdOrderByEffectiveFrom(goalId.value(), owner.value()).stream()
                .map(SavingsGoalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void saveRateChange(SavingsRateChange change) {
        rateChanges.save(new SavingsRateChangeJpaEntity(change.id(), change.goalId().value(), change.owner().value(),
                change.effectiveFrom(), change.rate().value(), change.createdAt()));
    }

    @Override
    public void deleteRateChangesAfter(SavingsGoalId goalId, UserId owner, LocalDate date) {
        rateChanges.deleteAfter(goalId.value(), owner.value(), date);
    }

    private static SavingsGoal toDomain(SavingsGoalJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        SavingsPlan plan = switch (SavingsPlanKind.valueOf(entity.getPlanKind())) {
            case TARGET_DATE -> new SavingsPlan.ByTargetDate(entity.getTargetDate());
            case MONTHLY_AMOUNT -> new SavingsPlan.ByMonthlyAmount(new Money(entity.getMonthlyAmount(), currency));
        };
        return SavingsGoal.restore(new SavingsGoalId(entity.getId()), new UserId(entity.getUserId()),
                entity.getName(), new Money(entity.getTargetAmount(), currency), plan, entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static SavingsContribution toDomain(SavingsContributionJpaEntity entity) {
        return new SavingsContribution(entity.getId(), new SavingsGoalId(entity.getGoalId()),
                new UserId(entity.getUserId()), entity.getContributionDate(),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())), entity.getCreatedAt());
    }

    private static SavingsRateChange toDomain(SavingsRateChangeJpaEntity entity) {
        return new SavingsRateChange(entity.getId(), new SavingsGoalId(entity.getGoalId()),
                new UserId(entity.getUserId()), entity.getEffectiveFrom(), new AnnualRate(entity.getAnnualRate()),
                entity.getCreatedAt());
    }
}
