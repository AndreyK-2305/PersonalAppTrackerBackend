package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** El valor o la fecha de un abono no cumple las reglas (ADR-0018). */
public class InvalidContributionException extends DomainException {

    /** Dato del abono que incumple una regla. */
    public enum Rule {
        AMOUNT("goals.contribution.amount_invalid"),
        DATE("goals.contribution.date_invalid");

        private final String code;

        Rule(String code) {
            this.code = code;
        }
    }

    public InvalidContributionException(Rule rule, String message) {
        super(rule.code, message);
    }
}
