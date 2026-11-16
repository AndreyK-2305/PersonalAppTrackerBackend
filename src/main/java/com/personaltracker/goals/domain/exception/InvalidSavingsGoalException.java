package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/**
 * Un dato de la meta de ahorro no cumple las reglas (ADR-0018). Cada regla tiene su código estable
 * para que el cliente sepa qué campo corregir.
 */
public class InvalidSavingsGoalException extends DomainException {

    /** Dato de la meta que incumple una regla. */
    public enum Rule {
        NAME("goals.savings_goal.name_invalid"),
        TARGET("goals.savings_goal.target_invalid"),
        TARGET_DATE("goals.savings_goal.target_date_invalid"),
        MONTHLY_AMOUNT("goals.savings_goal.monthly_amount_invalid");

        private final String code;

        Rule(String code) {
            this.code = code;
        }
    }

    public InvalidSavingsGoalException(Rule rule, String message) {
        super(rule.code, message);
    }
}
