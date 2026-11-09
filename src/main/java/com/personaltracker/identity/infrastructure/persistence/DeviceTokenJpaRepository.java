package com.personaltracker.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenJpaEntity, UUID> {

    Optional<DeviceTokenJpaEntity> findBySecretHash(String secretHash);

    Optional<DeviceTokenJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<DeviceTokenJpaEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
