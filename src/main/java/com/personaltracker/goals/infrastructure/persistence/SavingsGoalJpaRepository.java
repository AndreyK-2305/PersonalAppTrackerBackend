package com.personaltracker.goals.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavingsGoalJpaRepository extends JpaRepository<SavingsGoalJpaEntity, UUID> {

    Optional<SavingsGoalJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<SavingsGoalJpaEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
