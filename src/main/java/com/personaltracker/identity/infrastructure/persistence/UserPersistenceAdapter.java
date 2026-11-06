package com.personaltracker.identity.infrastructure.persistence;

import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link UserRepository} con JPA. Al guardar, copia el estado del dominio sobre la
 * entidad gestionada de la transacción, de modo que el bloqueo optimista (@Version) detecta
 * modificaciones concurrentes sin que el dominio conozca la versión.
 */
@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public void save(User user) {
        UserJpaEntity entity = repository.findById(user.id().value())
                .orElseGet(() -> new UserJpaEntity(user.id().value()));
        entity.setEmail(user.email().value());
        entity.setPasswordHash(user.passwordHash());
        entity.setDisplayName(user.displayName());
        entity.setTimeZone(user.timeZone().getId());
        entity.setFailedLoginAttempts(user.failedLoginAttempts());
        entity.setLockedUntil(user.lockedUntil());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        repository.save(entity);
    }

    private static User toDomain(UserJpaEntity entity) {
        return User.restore(
                new UserId(entity.getId()),
                new Email(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                ZoneId.of(entity.getTimeZone()),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
