package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.SavingsGoalView;
import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.exception.ContributionNotFoundException;
import com.personaltracker.goals.domain.exception.InvalidContributionException;
import com.personaltracker.goals.domain.exception.InvalidContributionException.Rule;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marcar y borrar abonos de una meta de ahorro. No crean movimientos en Finanzas (ADR-0018). */
@Service
public class RecordSavingsContributions {

    private final SavingsGoalRepository goals;
    private final SavingsGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    RecordSavingsContributions(
            SavingsGoalRepository goals, SavingsGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.goals = goals;
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * 404 si la meta no existe o es de otro usuario; 422 si el valor no es positivo o la fecha es
     * futura o tiene más de {@link SavingsContribution#MAX_AGE_YEARS} años.
     */
    @Transactional
    public SavingsGoalView add(UserId owner, SavingsGoalId goalId, LocalDate date, Money amount) {
        SavingsGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        if (date.isAfter(today)) {
            throw new InvalidContributionException(Rule.DATE, "La fecha del abono no puede ser futura.");
        }
        if (date.isBefore(today.minusYears(SavingsContribution.MAX_AGE_YEARS))) {
            throw new InvalidContributionException(Rule.DATE,
                    "La fecha del abono no puede tener más de " + SavingsContribution.MAX_AGE_YEARS + " años.");
        }
        if (!amount.currency().equals(goal.target().currency())) {
            throw new InvalidContributionException(Rule.AMOUNT, "El abono debe estar en la moneda de la meta.");
        }
        goals.saveContribution(
                new SavingsContribution(UUID.randomUUID(), goalId, owner, date, amount, clock.instant()));
        return reader.view(goal, today);
    }

    /** 404 si la meta o el abono no existen o son de otro usuario. */
    @Transactional
    public SavingsGoalView remove(UserId owner, SavingsGoalId goalId, UUID contributionId) {
        SavingsGoal goal = reader.require(owner, goalId);
        goals.deleteContribution(goals.findContribution(goalId, contributionId, owner)
                .orElseThrow(ContributionNotFoundException::new));
        return reader.view(goal, calendar.today(owner));
    }
}
