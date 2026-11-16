package com.personaltracker.goals.application.dto;

import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsProjection;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import java.util.List;

/** Una meta de ahorro con sus abonos, su historial de tasas y la proyección de hoy. */
public record SavingsGoalView(
        SavingsGoal goal,
        List<SavingsContribution> contributions,
        List<SavingsRateChange> rateChanges,
        SavingsProjection projection) {

    public SavingsGoalView {
        contributions = List.copyOf(contributions);
        rateChanges = List.copyOf(rateChanges);
    }
}
