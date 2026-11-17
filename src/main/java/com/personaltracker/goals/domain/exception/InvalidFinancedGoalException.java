package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/**
 * Un dato de la meta financiada no cumple las reglas (ADR-0018). Cada regla tiene su código estable
 * para que el cliente sepa qué campo corregir.
 */
public class InvalidFinancedGoalException extends DomainException {

    /** Dato de la meta financiada que incumple una regla. */
    public enum Rule {
        NAME("goals.financed_goal.name_invalid"),
        ASSET_VALUE("goals.financed_goal.asset_value_invalid"),
        DOWN_PAYMENT("goals.financed_goal.down_payment_invalid"),
        INSTALLMENTS("goals.financed_goal.installments_invalid"),
        FIRST_DUE_DATE("goals.financed_goal.first_due_date_invalid"),
        ACTUAL_INSTALLMENT("goals.financed_goal.actual_installment_invalid"),
        /** Con pagos, abonos o ajustes registrados, las condiciones ya no se pueden cambiar. */
        TERMS_LOCKED("goals.financed_goal.terms_locked");

        private final String code;

        Rule(String code) {
            this.code = code;
        }
    }

    public InvalidFinancedGoalException(Rule rule, String message) {
        super(rule.code, message);
    }
}
