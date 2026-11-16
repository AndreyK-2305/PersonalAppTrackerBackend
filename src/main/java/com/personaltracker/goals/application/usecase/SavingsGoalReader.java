package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.exception.SavingsGoalNotFoundException;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsProjection;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lee una meta de ahorro con todo lo que muestra la app: sus abonos, su historial de tasas y la
 * proyección de un día. Colaborador de los casos de uso, que comparten esta lectura.
 */
@Component
class SavingsGoalReader {

    private final SavingsGoalRepository goals;

    SavingsGoalReader(SavingsGoalRepository goals) {
        this.goals = goals;
    }

    /** 404 si la meta no existe o es de otro usuario. */
    SavingsGoal require(UserId owner, SavingsGoalId id) {
        return goals.findByIdAndOwner(id, owner).orElseThrow(SavingsGoalNotFoundException::new);
    }

    SavingsGoalView view(SavingsGoal goal, LocalDate today) {
        List<SavingsContribution> contributions = goals.contributions(goal.id(), goal.owner());
        List<SavingsRateChange> rates = goals.rateChanges(goal.id(), goal.owner());
        return new SavingsGoalView(goal, contributions, rates,
                SavingsProjection.of(goal, contributions, rates, today));
    }
}
