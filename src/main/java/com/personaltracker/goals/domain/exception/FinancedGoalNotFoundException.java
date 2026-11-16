package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La meta financiada no existe o pertenece a otro usuario. */
public class FinancedGoalNotFoundException extends NotFoundException {

    public FinancedGoalNotFoundException() {
        super("goals.financed_goal.not_found", "La meta no existe.");
    }
}
