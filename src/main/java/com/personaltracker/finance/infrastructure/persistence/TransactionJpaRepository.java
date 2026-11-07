package com.personaltracker.finance.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TransactionJpaRepository
        extends JpaRepository<TransactionJpaEntity, UUID>, JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select case when count(t) > 0 then true else false end
            from TransactionJpaEntity t
            where t.userId = :userId
              and (t.sourceAccountId = :accountId or t.destinationAccountId = :accountId)
            """)
    boolean existsByUserIdAndAccount(UUID userId, UUID accountId);

    boolean existsByUserIdAndCategoryIdIn(UUID userId, Collection<UUID> categoryIds);
}
