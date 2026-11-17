package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalCommand;
import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException;
import com.personaltracker.goals.domain.exception.InvalidFinancedGoalException.Rule;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crear, consultar, editar y borrar metas financiadas (ADR-0018). El plan de pagos se calcula al leer. */
@Service
public class ManageFinancedGoals {

    private final FinancedGoalRepository goals;
    private final FinancedGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    ManageFinancedGoals(FinancedGoalRepository goals, FinancedGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.goals = goals;
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FinancedGoalView> list(UserId owner) {
        LocalDate today = calendar.today(owner);
        return goals.findAllByOwner(owner).stream().map(goal -> reader.view(goal, today)).toList();
    }

    /** 404 si la meta no existe o es de otro usuario. */
    @Transactional(readOnly = true)
    public FinancedGoalView get(UserId owner, FinancedGoalId id) {
        return reader.view(reader.require(owner, id), calendar.today(owner));
    }

    /**
     * 422 si los datos no son válidos o la primera cuota está a más de 100 años de hoy. El plan se
     * calcula antes de guardar: si algún total no cabe en un monto, la meta no llega a existir.
     */
    @Transactional
    public FinancedGoalView create(UserId owner, FinancedGoalCommand command) {
        LocalDate today = calendar.today(owner);
        command.terms().requireFirstDueDateNear(today);
        FinancedGoal goal = FinancedGoal.create(FinancedGoalId.newId(), owner, command.name(), command.terms(),
                clock.instant());
        FinancedGoalView view = reader.view(goal, List.of(), today);
        goals.save(goal);
        return view;
    }

    /**
     * 404 si la meta no existe o es de otro usuario; 422 si los datos no son válidos.
     *
     * <ul>
     *   <li>Con pagos, abonos o ajustes registrados solo se puede cambiar el nombre: las condiciones son
     *       la base sobre la que se repasan esos registros.</li>
     *   <li>La fecha de la primera cuota solo se vuelve a comprobar contra hoy si cambia.</li>
     * </ul>
     */
    @Transactional
    public FinancedGoalView update(UserId owner, FinancedGoalId id, FinancedGoalCommand command) {
        FinancedGoal goal = reader.require(owner, id);
        List<FinancedRecord> records = reader.records(goal);
        LocalDate today = calendar.today(owner);
        boolean termsChange = !command.terms().equals(goal.terms());
        if (termsChange && !records.isEmpty()) {
            throw new InvalidFinancedGoalException(Rule.TERMS_LOCKED, "La meta ya tiene pagos, abonos o ajustes"
                    + " registrados: solo se puede cambiar el nombre. Para cambiar la tasa, registra un ajuste de tasa.");
        }
        if (!command.terms().firstDueDate().equals(goal.terms().firstDueDate())) {
            command.terms().requireFirstDueDateNear(today);
        }
        // El plan se calcula con las condiciones nuevas antes de tocar la meta: si falla, nada cambia.
        FinancedGoal updated = FinancedGoal.restore(goal.id(), goal.owner(), command.name(), command.terms(),
                goal.createdAt(), clock.instant());
        FinancedGoalView view = reader.view(updated, records, today);
        goal.update(command.name(), command.terms(), updated.updatedAt());
        goals.save(goal);
        return new FinancedGoalView(goal, view.plan(), view.status());
    }

    /** Borra la meta con sus registros. 404 si no existe o es de otro usuario. */
    @Transactional
    public void delete(UserId owner, FinancedGoalId id) {
        goals.delete(reader.require(owner, id));
    }
}
