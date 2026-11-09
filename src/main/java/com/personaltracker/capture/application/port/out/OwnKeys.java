package com.personaltracker.capture.application.port.out;

import com.personaltracker.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;

/** Llaves Bre-B propias del usuario (Ajustes → Perfil), cada una con el producto al que llega. */
public interface OwnKeys {

    /** El producto propio de la llave, comparada sin mayúsculas ni espacios. */
    Optional<UUID> productFor(UserId owner, String key);
}
