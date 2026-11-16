package com.personaltracker.goals.application.port.out;

import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Metas financiadas. Toda consulta filtra por propietario: una meta ajena se trata como inexistente. */
public interface FinancedGoalRepository {

    Optional<FinancedGoal> findByIdAndOwner(FinancedGoalId id, UserId owner);

    /** Las metas del usuario, de la más reciente a la más antigua. */
    List<FinancedGoal> findAllByOwner(UserId owner);

    void save(FinancedGoal goal);

    void delete(FinancedGoal goal);
}
