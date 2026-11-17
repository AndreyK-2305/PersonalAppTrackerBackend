package com.personaltracker.goals.application.dto;

import com.personaltracker.goals.domain.model.CreditPlan;
import com.personaltracker.goals.domain.model.CreditStatus;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.PaymentSchedule;

/** Una meta financiada con el estado de su crédito: el plan de pagos actual y cómo va hoy. */
public record FinancedGoalView(FinancedGoal goal, CreditPlan plan, CreditStatus status) {

    /** El plan de pagos actual, con las cuotas pagadas y las pendientes. */
    public PaymentSchedule schedule() {
        return plan.schedule();
    }
}
