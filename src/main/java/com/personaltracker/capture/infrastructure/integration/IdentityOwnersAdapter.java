package com.personaltracker.capture.infrastructure.integration;

import com.personaltracker.capture.application.port.out.CaptureOwners;
import com.personaltracker.capture.domain.model.OwnerName;
import com.personaltracker.identity.api.PersonNames;
import com.personaltracker.identity.api.UserTimeZones;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Zona horaria, nombres y apellidos del usuario a través de {@code identity :: api}. */
@Component
class IdentityOwnersAdapter implements CaptureOwners {

    private final UserTimeZones timeZones;
    private final PersonNames personNames;

    IdentityOwnersAdapter(UserTimeZones timeZones, PersonNames personNames) {
        this.timeZones = timeZones;
        this.personNames = personNames;
    }

    @Override
    public ZoneId timeZone(UserId owner) {
        return timeZones.of(owner);
    }

    @Override
    public OwnerName name(UserId owner) {
        PersonNames.Names names = personNames.of(owner);
        return OwnerName.of(names.givenNames(), names.surnames());
    }
}
