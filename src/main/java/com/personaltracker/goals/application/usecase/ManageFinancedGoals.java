package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalCommand;
import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.exception.FinancedGoalNotFoundException;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.PaymentSchedule;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crear, consultar, editar y borrar metas financiadas (ADR-0018). El plan de pagos se calcula al leer. */
@Service
public class ManageFinancedGoals {

    private final FinancedGoalRepository goals;
    private final GoalCalendar calendar;
    private final Clock clock;

    ManageFinancedGoals(FinancedGoalRepository goals, GoalCalendar calendar, Clock clock) {
        this.goals = goals;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FinancedGoalView> list(UserId owner) {
        return goals.findAllByOwner(owner).stream().map(FinancedGoalView::of).toList();
    }

    /** 404 si la meta no existe o es de otro usuario. */
    @Transactional(readOnly = true)
    public FinancedGoalView get(UserId owner, FinancedGoalId id) {
        return FinancedGoalView.of(require(owner, id));
    }

    /**
     * 422 si los datos no son válidos o la primera cuota está a más de 100 años de hoy. El plan se
     * calcula antes de guardar: si algún total no cabe en un monto, la meta no llega a existir.
     */
    @Transactional
    public FinancedGoalView create(UserId owner, FinancedGoalCommand command) {
        command.terms().requireFirstDueDateNear(calendar.today(owner));
        PaymentSchedule plan = command.terms().schedule();
        FinancedGoal goal = FinancedGoal.create(FinancedGoalId.newId(), owner, command.name(), command.terms(),
                clock.instant());
        goals.save(goal);
        return new FinancedGoalView(goal, plan);
    }

    /**
     * 404 si la meta no existe o es de otro usuario; 422 si los datos no son válidos. La fecha de la
     * primera cuota solo se vuelve a comprobar contra hoy si cambia.
     */
    @Transactional
    public FinancedGoalView update(UserId owner, FinancedGoalId id, FinancedGoalCommand command) {
        FinancedGoal goal = require(owner, id);
        if (!command.terms().firstDueDate().equals(goal.terms().firstDueDate())) {
            command.terms().requireFirstDueDateNear(calendar.today(owner));
        }
        PaymentSchedule plan = command.terms().schedule();
        goal.update(command.name(), command.terms(), clock.instant());
        goals.save(goal);
        return new FinancedGoalView(goal, plan);
    }

    /** 404 si la meta no existe o es de otro usuario. */
    @Transactional
    public void delete(UserId owner, FinancedGoalId id) {
        goals.delete(require(owner, id));
    }

    private FinancedGoal require(UserId owner, FinancedGoalId id) {
        return goals.findByIdAndOwner(id, owner).orElseThrow(FinancedGoalNotFoundException::new);
    }
}
