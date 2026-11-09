package com.personaltracker.identity.infrastructure.persistence;

import com.personaltracker.identity.application.port.out.DeviceTokenRepository;
import com.personaltracker.identity.domain.model.DeviceScope;
import com.personaltracker.identity.domain.model.DeviceToken;
import com.personaltracker.identity.domain.model.DeviceTokenId;
import com.personaltracker.shared.domain.UserId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Tokens de dispositivo sobre {@code identity.device_tokens}; los permisos se guardan separados por espacios. */
@Component
class DeviceTokenPersistenceAdapter implements DeviceTokenRepository {

    private static final Map<String, DeviceScope> SCOPES_BY_VALUE = Arrays.stream(DeviceScope.values())
            .collect(Collectors.toMap(DeviceScope::value, Function.identity()));

    private final DeviceTokenJpaRepository repository;

    DeviceTokenPersistenceAdapter(DeviceTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DeviceToken> findBySecretHash(String secretHash) {
        return repository.findBySecretHash(secretHash).map(DeviceTokenPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<DeviceToken> findByIdAndOwner(DeviceTokenId id, UserId owner) {
        return repository.findByIdAndUserId(id.value(), owner.value()).map(DeviceTokenPersistenceAdapter::toDomain);
    }

    @Override
    public List<DeviceToken> findAllByOwner(UserId owner) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(owner.value()).stream()
                .map(DeviceTokenPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void save(DeviceToken token) {
        DeviceTokenJpaEntity entity = repository.findById(token.id().value())
                .orElseGet(() -> new DeviceTokenJpaEntity(token.id().value()));
        entity.setUserId(token.owner().value());
        entity.setName(token.name());
        entity.setSecretHash(token.secretHash());
        entity.setScopes(token.scopes().stream().map(DeviceScope::value).sorted().collect(Collectors.joining(" ")));
        entity.setCreatedAt(token.createdAt());
        entity.setLastUsedAt(token.lastUsedAt());
        entity.setRevokedAt(token.revokedAt());
        repository.save(entity);
    }

    private static DeviceToken toDomain(DeviceTokenJpaEntity entity) {
        Set<DeviceScope> scopes = Arrays.stream(entity.getScopes().split(" "))
                .map(SCOPES_BY_VALUE::get)
                .filter(scope -> scope != null)
                .collect(Collectors.toSet());
        return DeviceToken.restore(new DeviceTokenId(entity.getId()), new UserId(entity.getUserId()),
                entity.getName(), entity.getSecretHash(), scopes, entity.getCreatedAt(), entity.getLastUsedAt(),
                entity.getRevokedAt());
    }
}
