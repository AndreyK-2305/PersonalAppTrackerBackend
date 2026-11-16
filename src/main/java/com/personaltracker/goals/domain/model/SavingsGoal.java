package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException.Rule;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Meta de ahorro (ADR-0018): un valor objetivo y un plan para alcanzarlo. Lo ahorrado sale de sus
 * abonos ({@link SavingsContribution}) y la proyección, de {@link SavingsProjection}.
 */
public final class SavingsGoal {

    public static final int MAX_NAME_LENGTH = 100;

    private final SavingsGoalId id;
    private final UserId owner;
    private String name;
    private Money target;
    private SavingsPlan plan;
    private final Instant createdAt;
    private Instant updatedAt;

    private SavingsGoal(SavingsGoalId id, UserId owner, String name, Money target, SavingsPlan plan,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = requireName(name);
        this.target = requireTarget(target);
        this.plan = requirePlan(plan, target);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static SavingsGoal create(
            SavingsGoalId id, UserId owner, String name, Money target, SavingsPlan plan, Instant now) {
        return new SavingsGoal(id, owner, name, target, plan, now, now);
    }

    /** Reconstruye una meta guardada. */
    public static SavingsGoal restore(SavingsGoalId id, UserId owner, String name, Money target, SavingsPlan plan,
            Instant createdAt, Instant updatedAt) {
        return new SavingsGoal(id, owner, name, target, plan, createdAt, updatedAt);
    }

    public void update(String newName, Money newTarget, SavingsPlan newPlan, Instant now) {
        String validName = requireName(newName);
        Money validTarget = requireTarget(newTarget);
        SavingsPlan validPlan = requirePlan(newPlan, validTarget);
        this.name = validName;
        this.target = validTarget;
        this.plan = validPlan;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidSavingsGoalException(Rule.NAME,
                    "El nombre es obligatorio y tiene máximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    private static Money requireTarget(Money target) {
        Objects.requireNonNull(target, "target");
        if (!target.isPositive()) {
            throw new InvalidSavingsGoalException(Rule.TARGET, "El valor objetivo debe ser mayor que cero.");
        }
        return target;
    }

    private static SavingsPlan requirePlan(SavingsPlan plan, Money target) {
        Objects.requireNonNull(plan, "plan");
        if (plan instanceof SavingsPlan.ByMonthlyAmount(Money monthly)
                && !monthly.currency().equals(target.currency())) {
            throw new InvalidSavingsGoalException(Rule.MONTHLY_AMOUNT,
                    "El aporte mensual debe estar en la moneda del objetivo.");
        }
        return plan;
    }

    public SavingsGoalId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Money target() {
        return target;
    }

    public SavingsPlan plan() {
        return plan;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
