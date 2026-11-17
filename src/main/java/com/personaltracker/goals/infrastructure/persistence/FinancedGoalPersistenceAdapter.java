package com.personaltracker.goals.infrastructure.persistence;

import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PrepaymentMode;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link FinancedGoalRepository} con JPA sobre {@code goals.financed_goals} y
 * {@code goals.financed_goal_records}. La llave foránea borra los registros con la meta.
 */
@Component
class FinancedGoalPersistenceAdapter implements FinancedGoalRepository {

    private final FinancedGoalJpaRepository repository;
    private final FinancedGoalRecordJpaRepository records;

    FinancedGoalPersistenceAdapter(FinancedGoalJpaRepository repository, FinancedGoalRecordJpaRepository records) {
        this.repository = repository;
        this.records = records;
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

    @Override
    public List<FinancedRecord> records(FinancedGoalId goalId, UserId owner) {
        return records.findAllByGoalIdAndUserIdOrderBySequence(goalId.value(), owner.value()).stream()
                .map(FinancedGoalPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void appendRecords(FinancedGoal goal, List<FinancedRecord> added) {
        int sequence = records.lastSequence(goal.id().value());
        String currency = goal.terms().assetValue().currency().getCurrencyCode();
        for (FinancedRecord record : added) {
            sequence++;
            records.save(toEntity(goal, record, sequence, currency));
        }
    }

    @Override
    public void deleteRecord(FinancedGoal goal, UUID recordId) {
        records.findByIdAndGoalIdAndUserId(recordId, goal.id().value(), goal.owner().value())
                .ifPresent(records::delete);
    }

    private static FinancedGoalRecordJpaEntity toEntity(FinancedGoal goal, FinancedRecord record, int sequence,
            String currency) {
        UUID goalId = goal.id().value();
        UUID userId = goal.owner().value();
        String kind = record.kind().name();
        return switch (record) {
            case InstallmentPayment payment -> new FinancedGoalRecordJpaEntity(payment.id(), goalId, userId, sequence,
                    kind, payment.date(), currency, payment.amount().amount(), payment.excessMode().name(), null,
                    null, payment.createdAt());
            case Prepayment prepayment -> new FinancedGoalRecordJpaEntity(prepayment.id(), goalId, userId, sequence,
                    kind, prepayment.date(), currency, prepayment.amount().amount(), prepayment.mode().name(), null,
                    null, prepayment.createdAt());
            case RateAdjustment adjustment -> new FinancedGoalRecordJpaEntity(adjustment.id(), goalId, userId,
                    sequence, kind, adjustment.date(), currency, null, null, adjustment.rate().value(),
                    adjustment.actualInstallment() == null ? null : adjustment.actualInstallment().amount(),
                    adjustment.createdAt());
        };
    }

    private static FinancedRecord toDomain(FinancedGoalRecordJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return switch (FinancedRecord.Kind.valueOf(entity.getKind())) {
            case PAYMENT -> new InstallmentPayment(entity.getId(), entity.getRecordDate(),
                    new Money(entity.getAmount(), currency), PrepaymentMode.valueOf(entity.getPrepaymentMode()),
                    entity.getCreatedAt());
            case PREPAYMENT -> new Prepayment(entity.getId(), entity.getRecordDate(),
                    new Money(entity.getAmount(), currency), PrepaymentMode.valueOf(entity.getPrepaymentMode()),
                    entity.getCreatedAt());
            case RATE_ADJUSTMENT -> new RateAdjustment(entity.getId(), entity.getRecordDate(),
                    new AnnualRate(entity.getAnnualRate()),
                    entity.getActualInstallment() == null ? null : new Money(entity.getActualInstallment(), currency),
                    entity.getCreatedAt());
        };
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
