package com.personaltracker.goals.application.port.out;

import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;

/** Zona horaria del usuario, para saber qué día es "hoy"; el adaptador usa {@code identity :: api}. */
public interface GoalOwners {

    ZoneId timeZone(UserId owner);
}
