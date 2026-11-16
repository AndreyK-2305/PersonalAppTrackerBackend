package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException.Rule;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Meta financiada (ADR-0018): un bien que se piensa comprar a crédito, con las condiciones de la
 * financiación. Es un simulador y plan de pagos: no crea obligaciones ni movimientos en Finanzas.
 */
public final class FinancedGoal {

    public static final int MAX_NAME_LENGTH = 100;

    private final FinancedGoalId id;
    private final UserId owner;
    private String name;
    private FinancedTerms terms;
    private final Instant createdAt;
    private Instant updatedAt;

    private FinancedGoal(FinancedGoalId id, UserId owner, String name, FinancedTerms terms, Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = requireName(name);
        this.terms = Objects.requireNonNull(terms, "terms");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static FinancedGoal create(FinancedGoalId id, UserId owner, String name, FinancedTerms terms, Instant now) {
        return new FinancedGoal(id, owner, name, terms, now, now);
    }

    /** Reconstruye una meta guardada. */
    public static FinancedGoal restore(FinancedGoalId id, UserId owner, String name, FinancedTerms terms,
            Instant createdAt, Instant updatedAt) {
        return new FinancedGoal(id, owner, name, terms, createdAt, updatedAt);
    }

    public void update(String newName, FinancedTerms newTerms, Instant now) {
        String validName = requireName(newName);
        this.terms = Objects.requireNonNull(newTerms, "newTerms");
        this.name = validName;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidFinancedGoalException(Rule.NAME,
                    "El nombre es obligatorio y tiene máximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    public FinancedGoalId id() {
        return id;
    }

    public UserId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public FinancedTerms terms() {
        return terms;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
