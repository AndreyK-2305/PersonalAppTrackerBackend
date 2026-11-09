package com.personaltracker.capture.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OwnKeyJpaRepository extends JpaRepository<OwnKeyJpaEntity, UUID> {

    Optional<OwnKeyJpaEntity> findByUserIdAndKeyValue(UUID userId, String keyValue);

    Optional<OwnKeyJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<OwnKeyJpaEntity> findAllByUserIdOrderByKeyValue(UUID userId);
}
