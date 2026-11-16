package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.port.out.GoalOwners;
import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dobles en memoria de los puertos de {@code goals}, con el mismo contrato que los adaptadores reales. */
final class GoalsFakes {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    static final Currency COP = Currency.getInstance("COP");

    private GoalsFakes() {}

    static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    /** Los casos de uso de metas de ahorro cableados con dobles. */
    static final class Module {

        final Goals goals = new Goals();
        final Owners owners = new Owners();
        final MutableClock clock;
        final ManageSavingsGoals manage;
        final RecordSavingsContributions contributions;
        final AdjustSavingsRate rate;

        Module(Instant now) {
            clock = new MutableClock(now);
            GoalCalendar calendar = new GoalCalendar(owners, clock);
            SavingsGoalReader reader = new SavingsGoalReader(goals);
            manage = new ManageSavingsGoals(goals, reader, calendar, clock);
            contributions = new RecordSavingsContributions(goals, reader, calendar, clock);
            rate = new AdjustSavingsRate(goals, reader, calendar, clock);
        }
    }

    static final class Goals implements SavingsGoalRepository {

        final Map<SavingsGoalId, SavingsGoal> stored = new LinkedHashMap<>();
        final List<SavingsContribution> contributions = new ArrayList<>();
        final List<SavingsRateChange> rateChanges = new ArrayList<>();

        @Override
        public Optional<SavingsGoal> findByIdAndOwner(SavingsGoalId id, UserId owner) {
            return Optional.ofNullable(stored.get(id)).filter(goal -> goal.owner().equals(owner));
        }

        @Override
        public List<SavingsGoal> findAllByOwner(UserId owner) {
            return stored.values().stream()
                    .filter(goal -> goal.owner().equals(owner))
                    .sorted(Comparator.comparing(SavingsGoal::createdAt).reversed())
                    .toList();
        }

        @Override
        public void save(SavingsGoal goal) {
            stored.put(goal.id(), goal);
        }

        @Override
        public void delete(SavingsGoal goal) {
            stored.remove(goal.id());
            contributions.removeIf(contribution -> contribution.goalId().equals(goal.id()));
            rateChanges.removeIf(change -> change.goalId().equals(goal.id()));
        }

        @Override
        public List<SavingsContribution> contributions(SavingsGoalId goalId, UserId owner) {
            return contributions.stream()
                    .filter(contribution -> contribution.goalId().equals(goalId) && contribution.owner().equals(owner))
                    .sorted(Comparator.comparing(SavingsContribution::date)
                            .thenComparing(SavingsContribution::createdAt)
                            .reversed())
                    .toList();
        }

        @Override
        public Optional<SavingsContribution> findContribution(
                SavingsGoalId goalId, UUID contributionId, UserId owner) {
            return contributions(goalId, owner).stream()
                    .filter(contribution -> contribution.id().equals(contributionId))
                    .findFirst();
        }

        @Override
        public void saveContribution(SavingsContribution contribution) {
            contributions.add(contribution);
        }

        @Override
        public void deleteContribution(SavingsContribution contribution) {
            contributions.removeIf(stored -> stored.id().equals(contribution.id()));
        }

        @Override
        public List<SavingsRateChange> rateChanges(SavingsGoalId goalId, UserId owner) {
            return rateChanges.stream()
                    .filter(change -> change.goalId().equals(goalId) && change.owner().equals(owner))
                    .sorted(Comparator.comparing(SavingsRateChange::effectiveFrom))
                    .toList();
        }

        /** Como la restricción única de la base: no admite dos cambios de una meta para el mismo día. */
        @Override
        public void saveRateChange(SavingsRateChange change) {
            boolean duplicated = rateChanges.stream().anyMatch(stored -> stored.goalId().equals(change.goalId())
                    && stored.effectiveFrom().equals(change.effectiveFrom()));
            if (duplicated) {
                throw new IllegalStateException("Ya hay un cambio de tasa para " + change.effectiveFrom());
            }
            rateChanges.add(change);
        }

        @Override
        public void deleteRateChangesAfter(SavingsGoalId goalId, UserId owner, LocalDate date) {
            rateChanges.removeIf(change -> change.goalId().equals(goalId) && change.owner().equals(owner)
                    && change.effectiveFrom().isAfter(date));
        }
    }

    /** Zona horaria por usuario; Bogotá si no se indica otra. */
    static final class Owners implements GoalOwners {

        final Map<UserId, ZoneId> zones = new HashMap<>();

        @Override
        public ZoneId timeZone(UserId owner) {
            return zones.getOrDefault(owner, BOGOTA);
        }
    }

    /** Reloj que la prueba adelanta a mano. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advanceDays(long days) {
            this.now = now.plusSeconds(days * 86_400);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
