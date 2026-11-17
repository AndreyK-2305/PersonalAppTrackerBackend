package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.exception.FinancedRecordNotFoundException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException.Rule;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deshace el último registro de una meta financiada (ADR-0018). Solo el último: los anteriores son la
 * base sobre la que se calcularon los siguientes.
 */
@Service
public class UndoCreditRecord {

    private final FinancedGoalRepository goals;
    private final FinancedGoalReader reader;
    private final GoalCalendar calendar;

    UndoCreditRecord(FinancedGoalRepository goals, FinancedGoalReader reader, GoalCalendar calendar) {
        this.goals = goals;
        this.reader = reader;
        this.calendar = calendar;
    }

    /** 404 si la meta o el registro no existen o son de otro usuario; 422 si no es el último registro. */
    @Transactional
    public FinancedGoalView undo(UserId owner, FinancedGoalId goalId, UUID recordId) {
        FinancedGoal goal = reader.require(owner, goalId);
        List<FinancedRecord> records = reader.records(goal);
        boolean exists = records.stream().anyMatch(record -> record.id().equals(recordId));
        if (!exists) {
            throw new FinancedRecordNotFoundException();
        }
        if (!records.get(records.size() - 1).id().equals(recordId)) {
            throw new InvalidFinancedRecordException(Rule.NOT_LAST, "Solo se puede deshacer el último registro.");
        }
        goals.deleteRecord(goal, recordId);
        return reader.view(goal, records.subList(0, records.size() - 1), calendar.today(owner));
    }
}
