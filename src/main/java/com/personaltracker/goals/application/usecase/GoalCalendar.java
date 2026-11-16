package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.port.out.GoalOwners;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Qué día es hoy para un usuario, según la zona horaria de su perfil. Colaborador de los casos de uso. */
@Component
class GoalCalendar {

    private final GoalOwners owners;
    private final Clock clock;

    GoalCalendar(GoalOwners owners, Clock clock) {
        this.owners = owners;
        this.clock = clock;
    }

    LocalDate today(UserId owner) {
        return LocalDate.now(clock.withZone(owners.timeZone(owner)));
    }
}
