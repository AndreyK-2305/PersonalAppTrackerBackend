package com.personaltracker.goals.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface SavingsRateChangeJpaRepository extends JpaRepository<SavingsRateChangeJpaEntity, UUID> {

    List<SavingsRateChangeJpaEntity> findAllByGoalIdAndUserIdOrderByEffectiveFrom(UUID goalId, UUID userId);

    /**
     * Se ejecuta de inmediato, antes del insert del cambio nuevo con la misma fecha de inicio. Lleva
     * {@code @Transactional} porque Spring Data ejecuta las consultas declaradas en solo lectura; dentro
     * de un caso de uso se une a su transacción.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from SavingsRateChangeJpaEntity r where r.goalId = :goalId and r.userId = :userId"
            + " and r.effectiveFrom > :date")
    void deleteAfter(@Param("goalId") UUID goalId, @Param("userId") UUID userId, @Param("date") LocalDate date);
}
