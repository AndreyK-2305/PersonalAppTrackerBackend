package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.exception.FinancedGoalNotFoundException;
import com.personaltracker.goals.domain.model.CreditPlan;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lee una meta financiada con sus registros y calcula el estado de su crédito. También agrega
 * registros: antes de guardarlos repasa el plan completo, que es donde se validan las reglas, así
 * que un registro inválido nunca llega a la base. Colaborador de los casos de uso.
 */
@Component
class FinancedGoalReader {

    private final FinancedGoalRepository goals;

    FinancedGoalReader(FinancedGoalRepository goals) {
        this.goals = goals;
    }

    /** 404 si la meta no existe o es de otro usuario. */
    FinancedGoal require(UserId owner, FinancedGoalId id) {
        return goals.findByIdAndOwner(id, owner).orElseThrow(FinancedGoalNotFoundException::new);
    }

    List<FinancedRecord> records(FinancedGoal goal) {
        return goals.records(goal.id(), goal.owner());
    }

    FinancedGoalView view(FinancedGoal goal, LocalDate today) {
        return view(goal, records(goal), today);
    }

    FinancedGoalView view(FinancedGoal goal, List<FinancedRecord> records, LocalDate today) {
        CreditPlan plan = CreditPlan.of(goal.terms(), records);
        return new FinancedGoalView(goal, plan, plan.statusOn(today));
    }

    /** Valida los registros nuevos repasando el plan con ellos y, si pasan, los guarda al final. */
    FinancedGoalView append(FinancedGoal goal, List<FinancedRecord> existing, List<FinancedRecord> added,
            LocalDate today) {
        List<FinancedRecord> all = new ArrayList<>(existing);
        all.addAll(added);
        FinancedGoalView view = view(goal, all, today);
        goals.appendRecords(goal, added);
        return view;
    }
}
