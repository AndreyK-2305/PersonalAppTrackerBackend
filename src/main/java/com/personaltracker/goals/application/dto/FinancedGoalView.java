package com.personaltracker.goals.application.dto;

import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.PaymentSchedule;

/** Una meta financiada con su plan de pagos calculado. */
public record FinancedGoalView(FinancedGoal goal, PaymentSchedule schedule) {

    public static FinancedGoalView of(FinancedGoal goal) {
        return new FinancedGoalView(goal, goal.terms().schedule());
    }
}
