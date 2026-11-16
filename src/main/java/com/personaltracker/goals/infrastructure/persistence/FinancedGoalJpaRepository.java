package com.personaltracker.goals.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FinancedGoalJpaRepository extends JpaRepository<FinancedGoalJpaEntity, UUID> {

    Optional<FinancedGoalJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<FinancedGoalJpaEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
