package com.personaltracker.goals.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FinancedGoalRecordJpaRepository extends JpaRepository<FinancedGoalRecordJpaEntity, UUID> {

    List<FinancedGoalRecordJpaEntity> findAllByGoalIdAndUserIdOrderBySequence(UUID goalId, UUID userId);

    Optional<FinancedGoalRecordJpaEntity> findByIdAndGoalIdAndUserId(UUID id, UUID goalId, UUID userId);

    /** El orden del último registro de la meta, o 0 si no tiene ninguno. */
    @Query("select coalesce(max(r.sequence), 0) from FinancedGoalRecordJpaEntity r where r.goalId = :goalId")
    int lastSequence(@Param("goalId") UUID goalId);
}
