package com.personaltracker.goals.infrastructure.integration;

import com.personaltracker.goals.application.port.out.GoalOwners;
import com.personaltracker.identity.api.UserTimeZones;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Zona horaria del usuario a través de {@code identity :: api}. */
@Component
class IdentityGoalOwnersAdapter implements GoalOwners {

    private final UserTimeZones timeZones;

    IdentityGoalOwnersAdapter(UserTimeZones timeZones) {
        this.timeZones = timeZones;
    }

    @Override
    public ZoneId timeZone(UserId owner) {
        return timeZones.of(owner);
    }
}
