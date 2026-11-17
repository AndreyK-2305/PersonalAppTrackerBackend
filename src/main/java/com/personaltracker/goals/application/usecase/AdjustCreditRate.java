package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajuste de tasa de una meta financiada, para créditos de tasa variable (ADR-0018). La tasa nueva
 * rige desde la siguiente cuota pendiente y la cuota se recalcula sobre el saldo y las cuotas que
 * faltan.
 */
@Service
public class AdjustCreditRate {

    private final FinancedGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    AdjustCreditRate(FinancedGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * @param actualInstallment nueva cuota real, o {@code null} para conservar los mismos seguros y otros
     *
     * <p>404 si la meta no existe o es de otro usuario; 422 si la cuota real es menor que la calculada
     * con la nueva tasa o el crédito ya está pagado.
     */
    @Transactional
    public FinancedGoalView adjust(UserId owner, FinancedGoalId goalId, AnnualRate rate, Money actualInstallment) {
        FinancedGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        FinancedRecord adjustment =
                new RateAdjustment(UUID.randomUUID(), today, rate, actualInstallment, clock.instant());
        return reader.append(goal, reader.records(goal), List.of(adjustment), today);
    }
}
