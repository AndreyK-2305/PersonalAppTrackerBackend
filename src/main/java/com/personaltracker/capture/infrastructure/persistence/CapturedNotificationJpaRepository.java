package com.personaltracker.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CapturedNotificationJpaRepository extends JpaRepository<CapturedNotificationJpaEntity, UUID> {

    /** Notificaciones con movimiento y huella en el rango, con las que no se ha emparejado ninguna otra. */
    @Query("""
            select n from CapturedNotificationJpaEntity n
            where n.userId = :userId
              and n.status in ('RECORDED', 'REVIEW')
              and n.transactionId is not null
              and n.occurredAt between :from and :to
              and not exists (
                  select 1 from CapturedNotificationJpaEntity p where p.pairedWith = n.id)
            """)
    List<CapturedNotificationJpaEntity> findUnpairedWithMovement(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);
}
