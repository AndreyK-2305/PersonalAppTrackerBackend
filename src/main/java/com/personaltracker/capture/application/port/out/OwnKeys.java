package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.OwnKey;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Llaves Bre-B propias del usuario (Ajustes → Perfil), cada una con el producto al que llega. */
public interface OwnKeys {

    /** El producto propio de la llave, comparada sin mayúsculas ni espacios a los lados. */
    Optional<UUID> productFor(UserId owner, String key);

    /** Las llaves del usuario, en orden alfabético. */
    List<OwnKey> list(UserId owner);

    Optional<OwnKey> findByIdAndOwner(UUID id, UserId owner);

    boolean exists(UserId owner, String normalizedKey);

    void save(OwnKey key);

    void delete(OwnKey key);
}
