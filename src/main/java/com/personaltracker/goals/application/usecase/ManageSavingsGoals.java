package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.SavingsGoalCommand;
import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException.Rule;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsProjection;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crear, consultar, editar y borrar metas de ahorro (ADR-0018). */
@Service
public class ManageSavingsGoals {

    private final SavingsGoalRepository goals;
    private final SavingsGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    ManageSavingsGoals(SavingsGoalRepository goals, SavingsGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.goals = goals;
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavingsGoalView> list(UserId owner) {
        LocalDate today = calendar.today(owner);
        return goals.findAllByOwner(owner).stream().map(goal -> reader.view(goal, today)).toList();
    }

    /** 404 si la meta no existe o es de otro usuario. */
    @Transactional(readOnly = true)
    public SavingsGoalView get(UserId owner, SavingsGoalId id) {
        return reader.view(reader.require(owner, id), calendar.today(owner));
    }

    /** 422 si los datos no son válidos o la fecha objetivo no está entre mañana y 100 años. */
    @Transactional
    public SavingsGoalView create(UserId owner, SavingsGoalCommand command) {
        LocalDate today = calendar.today(owner);
        requireReachableDate(command.plan(), today);
        SavingsGoal goal = SavingsGoal.create(SavingsGoalId.newId(), owner, command.name(), command.target(),
                command.plan(), clock.instant());
        goals.save(goal);
        return reader.view(goal, today);
    }

    /**
     * 404 si la meta no existe o es de otro usuario; 422 si los datos no son válidos. Una fecha objetivo
     * nueva debe estar entre mañana y 100 años; conservar la de una meta vencida sí se permite.
     */
    @Transactional
    public SavingsGoalView update(UserId owner, SavingsGoalId id, SavingsGoalCommand command) {
        SavingsGoal goal = reader.require(owner, id);
        LocalDate today = calendar.today(owner);
        if (!command.plan().equals(goal.plan())) {
            requireReachableDate(command.plan(), today);
        }
        goal.update(command.name(), command.target(), command.plan(), clock.instant());
        goals.save(goal);
        return reader.view(goal, today);
    }

    /** Borra la meta con sus abonos y tasas. 404 si no existe o es de otro usuario. */
    @Transactional
    public void delete(UserId owner, SavingsGoalId id) {
        goals.delete(reader.require(owner, id));
    }

    /** La fecha objetivo va desde mañana hasta el plazo máximo de una meta ({@link SavingsProjection#MAX_MONTHS}). */
    private static void requireReachableDate(SavingsPlan plan, LocalDate today) {
        if (!(plan instanceof SavingsPlan.ByTargetDate(LocalDate targetDate))) {
            return;
        }
        if (!targetDate.isAfter(today)) {
            throw new InvalidSavingsGoalException(Rule.TARGET_DATE, "La fecha objetivo debe ser posterior a hoy.");
        }
        if (targetDate.isAfter(today.plusMonths(SavingsProjection.MAX_MONTHS))) {
            throw new InvalidSavingsGoalException(Rule.TARGET_DATE,
                    "La fecha objetivo debe estar dentro de los próximos 100 años.");
        }
    }
}
