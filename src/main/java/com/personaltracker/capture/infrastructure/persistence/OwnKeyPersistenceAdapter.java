package com.personaltracker.capture.infrastructure.persistence;

import com.personaltracker.capture.application.port.out.OwnKeys;
import com.personaltracker.capture.domain.model.OwnKey;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Llaves Bre-B propias sobre {@code capture.own_keys}. La llave llega ya normalizada desde el dominio. */
@Component
class OwnKeyPersistenceAdapter implements OwnKeys {

    private final OwnKeyJpaRepository repository;

    OwnKeyPersistenceAdapter(OwnKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> productFor(UserId owner, String key) {
        return repository.findByUserIdAndKeyValue(owner.value(), OwnKey.normalize(key))
                .map(OwnKeyJpaEntity::getProductId);
    }

    @Override
    public List<OwnKey> list(UserId owner) {
        return repository.findAllByUserIdOrderByKeyValue(owner.value()).stream()
                .map(OwnKeyPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<OwnKey> findByIdAndOwner(UUID id, UserId owner) {
        return repository.findByIdAndUserId(id, owner.value()).map(OwnKeyPersistenceAdapter::toDomain);
    }

    @Override
    public boolean exists(UserId owner, String normalizedKey) {
        return repository.findByUserIdAndKeyValue(owner.value(), normalizedKey).isPresent();
    }

    @Override
    public void save(OwnKey key) {
        repository.save(new OwnKeyJpaEntity(key.id(), key.owner().value(), key.key(), key.productId(), key.createdAt()));
    }

    @Override
    public void delete(OwnKey key) {
        repository.deleteById(key.id());
    }

    private static OwnKey toDomain(OwnKeyJpaEntity entity) {
        return new OwnKey(entity.getId(), new UserId(entity.getUserId()), entity.getKeyValue(),
                entity.getProductId(), entity.getCreatedAt());
    }
}
