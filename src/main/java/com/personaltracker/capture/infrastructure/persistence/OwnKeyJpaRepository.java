package com.personaltracker.capture.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OwnKeyJpaRepository extends JpaRepository<OwnKeyJpaEntity, UUID> {

    Optional<OwnKeyJpaEntity> findByUserIdAndKeyValue(UUID userId, String keyValue);
}
