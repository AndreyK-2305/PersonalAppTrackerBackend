package com.personaltracker.goals.application.port.out;

import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Metas financiadas con sus registros (pagos, abonos y ajustes de tasa). Toda consulta filtra por
 * propietario: una meta ajena se trata como inexistente. Borrar una meta borra también sus registros.
 */
public interface FinancedGoalRepository {

    Optional<FinancedGoal> findByIdAndOwner(FinancedGoalId id, UserId owner);

    /** Las metas del usuario, de la más reciente a la más antigua. */
    List<FinancedGoal> findAllByOwner(UserId owner);

    void save(FinancedGoal goal);

    void delete(FinancedGoal goal);

    /** Registros de la meta en el orden en que se hicieron. */
    List<FinancedRecord> records(FinancedGoalId goalId, UserId owner);

    /**
     * Agrega registros al final, en el orden dado. Si otra petición agregó uno al mismo tiempo, falla
     * por la restricción única del orden y la API responde 409.
     */
    void appendRecords(FinancedGoal goal, List<FinancedRecord> records);

    /** Borra un registro de la meta; no hace nada si no existe. */
    void deleteRecord(FinancedGoal goal, UUID recordId);
}
