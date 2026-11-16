package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PaymentSchedule;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula el plan de pagos de unas condiciones sin guardar nada (ADR-0018). Es la vista previa del
 * formulario y aplica las mismas reglas que al crear la meta.
 */
@Service
public class SimulateFinancing {

    private final GoalCalendar calendar;

    SimulateFinancing(GoalCalendar calendar) {
        this.calendar = calendar;
    }

    /** 422 si la primera cuota está a más de 100 años de hoy. Solo lee la zona horaria del usuario. */
    @Transactional(readOnly = true)
    public PaymentSchedule simulate(UserId owner, FinancedTerms terms) {
        terms.requireFirstDueDateNear(calendar.today(owner));
        return terms.schedule();
    }
}
