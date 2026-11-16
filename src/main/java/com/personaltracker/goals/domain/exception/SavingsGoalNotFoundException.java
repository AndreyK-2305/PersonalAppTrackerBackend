package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** La meta no existe o pertenece a otro usuario. */
public class SavingsGoalNotFoundException extends NotFoundException {

    public SavingsGoalNotFoundException() {
        super("goals.savings_goal.not_found", "La meta no existe.");
    }
}
