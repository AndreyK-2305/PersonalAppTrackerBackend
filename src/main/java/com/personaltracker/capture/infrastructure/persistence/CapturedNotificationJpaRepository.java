package com.personaltracker.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CapturedNotificationJpaRepository extends JpaRepository<CapturedNotificationJpaEntity, UUID> {

    boolean existsByUserIdAndContentHash(UUID userId, String contentHash);

    Optional<CapturedNotificationJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<CapturedNotificationJpaEntity> findFirstByUserIdAndTransactionId(UUID userId, UUID transactionId);

    List<CapturedNotificationJpaEntity> findAllByUserIdAndStatusOrderByReceivedAtDesc(UUID userId, String status);

    /** Bloquea la fila pendiente; si otra transacción la tiene bloqueada, la salta. */
    @Query(value = """
            SELECT * FROM capture.notifications
            WHERE id = :id AND status = 'RECEIVED'
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<CapturedNotificationJpaEntity> lockPending(@Param("id") UUID id);

    /** Las de Wallet van al final: su aviso repite el del banco con menos datos. */
    @Query(value = """
            SELECT id FROM capture.notifications
            WHERE status = 'RECEIVED' AND created_at <= :receivedBefore
            ORDER BY CASE WHEN lower(app) IN ('wallet', 'cartera') THEN 1 ELSE 0 END, created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findReadyToProcess(@Param("receivedBefore") Instant receivedBefore, @Param("limit") int limit);

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

    @Modifying
    @Query(value = """
            UPDATE capture.notifications
            SET title = NULL, body = NULL, raw_purged_at = :now, version = version + 1
            WHERE received_at < :receivedBefore AND raw_purged_at IS NULL
            """, nativeQuery = true)
    int purgeRawText(@Param("receivedBefore") Instant receivedBefore, @Param("now") Instant now);
}
