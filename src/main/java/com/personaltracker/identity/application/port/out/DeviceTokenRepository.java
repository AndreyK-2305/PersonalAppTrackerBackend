package com.personaltracker.identity.application.port.out;

import com.personaltracker.identity.domain.model.DeviceToken;
import com.personaltracker.identity.domain.model.DeviceTokenId;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

    Optional<DeviceToken> findBySecretHash(String secretHash);

    Optional<DeviceToken> findByIdAndOwner(DeviceTokenId id, UserId owner);

    /** Los tokens del usuario, los más recientes primero, incluidos los revocados. */
    List<DeviceToken> findAllByOwner(UserId owner);

    void save(DeviceToken token);
}
