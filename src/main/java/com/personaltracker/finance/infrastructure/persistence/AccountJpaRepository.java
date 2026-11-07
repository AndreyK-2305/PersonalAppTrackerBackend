package com.personaltracker.finance.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<AccountJpaEntity> findByUserIdOrderByCreatedAt(UUID userId);

    Optional<AccountJpaEntity> findByUserIdAndKind(UUID userId, String kind);
}
