package com.personaltracker.capture.application.port.out;

import com.personaltracker.capture.domain.model.OwnedProduct;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.UUID;

/** Productos del usuario en {@code finance}; el adaptador usa {@code finance :: api}. */
public interface UserProducts {

    /** Productos activos (no archivados), sin "Otro". */
    List<OwnedProduct> activeProducts(UserId owner);

    /** El producto de sistema "Otro", que recibe lo que no se identifica. */
    UUID otherProduct(UserId owner);
}
