package com.personaltracker.goals.application.port.out;

import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Metas de ahorro con sus abonos y cambios de tasa. Toda consulta filtra por propietario: una meta
 * ajena se trata como inexistente. Borrar una meta borra también sus abonos y tasas.
 */
public interface SavingsGoalRepository {

    Optional<SavingsGoal> findByIdAndOwner(SavingsGoalId id, UserId owner);

    /** Las metas del usuario, de la más reciente a la más antigua. */
    List<SavingsGoal> findAllByOwner(UserId owner);

    void save(SavingsGoal goal);

    void delete(SavingsGoal goal);

    /** Abonos de la meta, del más reciente al más antiguo. */
    List<SavingsContribution> contributions(SavingsGoalId goalId, UserId owner);

    Optional<SavingsContribution> findContribution(SavingsGoalId goalId, UUID contributionId, UserId owner);

    void saveContribution(SavingsContribution contribution);

    void deleteContribution(SavingsContribution contribution);

    /** Cambios de tasa de la meta, por fecha de inicio ascendente. */
    List<SavingsRateChange> rateChanges(SavingsGoalId goalId, UserId owner);

    /**
     * Guarda un cambio nuevo. Solo puede haber uno por meta y fecha de inicio: quien llama borra antes
     * el que aún no rige con {@link #deleteRateChangesAfter}; si no, falla por la restricción única.
     */
    void saveRateChange(SavingsRateChange change);

    /** Borra los cambios que aún no rigen ({@code effectiveFrom} posterior a {@code date}). */
    void deleteRateChangesAfter(SavingsGoalId goalId, UserId owner, LocalDate date);
}
