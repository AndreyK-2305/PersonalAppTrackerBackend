package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsRateChange;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activa, cambia o apaga (tasa 0) la rentabilidad de una meta de ahorro. La tasa nueva rige desde el
 * día siguiente; si ya había un cambio pendiente, lo reemplaza (ADR-0018).
 */
@Service
public class AdjustSavingsRate {

    private final SavingsGoalRepository goals;
    private final SavingsGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    AdjustSavingsRate(SavingsGoalRepository goals, SavingsGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.goals = goals;
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** 404 si la meta no existe o es de otro usuario. */
    @Transactional
    public SavingsGoalView adjust(UserId owner, SavingsGoalId goalId, AnnualRate rate) {
        SavingsGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        goals.deleteRateChangesAfter(goalId, owner, today);
        goals.saveRateChange(
                new SavingsRateChange(UUID.randomUUID(), goalId, owner, today.plusDays(1), rate, clock.instant()));
        return reader.view(goal, today);
    }
}
