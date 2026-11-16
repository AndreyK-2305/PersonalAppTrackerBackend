package com.personaltracker.goals.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavingsContributionJpaRepository extends JpaRepository<SavingsContributionJpaEntity, UUID> {

    List<SavingsContributionJpaEntity> findAllByGoalIdAndUserIdOrderByContributionDateDescCreatedAtDesc(
            UUID goalId, UUID userId);

    Optional<SavingsContributionJpaEntity> findByIdAndGoalIdAndUserId(UUID id, UUID goalId, UUID userId);
}
