package com.personaltracker.capture.infrastructure.persistence;

import com.personaltracker.capture.application.port.out.OwnKeys;
import com.personaltracker.shared.domain.UserId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Llaves Bre-B propias. La consulta normaliza la llave igual que al guardarla: sin espacios a los
 * lados y en minúsculas. La gestión (agregar, listar y borrar) llega con Ajustes → Perfil.
 */
@Component
class OwnKeyPersistenceAdapter implements OwnKeys {

    private final OwnKeyJpaRepository repository;

    OwnKeyPersistenceAdapter(OwnKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> productFor(UserId owner, String key) {
        return repository.findByUserIdAndKeyValue(owner.value(), normalize(key)).map(OwnKeyJpaEntity::getProductId);
    }

    static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
