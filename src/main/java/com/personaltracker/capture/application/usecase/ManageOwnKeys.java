package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.OwnKeys;
import com.personaltracker.capture.application.port.out.UserProducts;
import com.personaltracker.capture.domain.exception.OwnKeyNotFoundException;
import com.personaltracker.capture.domain.exception.OwnKeyProductInvalidException;
import com.personaltracker.capture.domain.exception.OwnKeyTakenException;
import com.personaltracker.capture.domain.model.OwnKey;
import com.personaltracker.capture.domain.model.OwnedProductKind;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Llaves Bre-B propias (Ajustes → Perfil, ADR-0017). Una llave solo puede apuntar a una cuenta o
 * billetera activa del usuario: es adonde llega el dinero de una transferencia a esa llave.
 */
@Service
public class ManageOwnKeys {

    private final OwnKeys ownKeys;
    private final UserProducts products;
    private final Clock clock;

    public ManageOwnKeys(OwnKeys ownKeys, UserProducts products, Clock clock) {
        this.ownKeys = ownKeys;
        this.products = products;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OwnKey> list(UserId owner) {
        return ownKeys.list(owner);
    }

    /** 422 si la llave no es válida o el producto no es una cuenta activa; 409 si ya existe. */
    @Transactional
    public OwnKey add(UserId owner, String key, UUID productId) {
        OwnKey ownKey = new OwnKey(UUID.randomUUID(), owner, key, productId, clock.instant());
        boolean depositProduct = products.activeProducts(owner).stream()
                .anyMatch(product -> product.id().equals(productId) && product.kind() == OwnedProductKind.DEPOSIT);
        if (!depositProduct) {
            throw new OwnKeyProductInvalidException();
        }
        if (ownKeys.exists(owner, ownKey.key())) {
            throw new OwnKeyTakenException();
        }
        ownKeys.save(ownKey);
        return ownKey;
    }

    /** 404 si la llave no existe o es de otro usuario. */
    @Transactional
    public void remove(UserId owner, UUID id) {
        ownKeys.delete(ownKeys.findByIdAndOwner(id, owner).orElseThrow(OwnKeyNotFoundException::new));
    }
}
