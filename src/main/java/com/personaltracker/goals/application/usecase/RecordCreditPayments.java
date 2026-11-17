package com.personaltracker.goals.application.usecase;

import com.personaltracker.goals.application.dto.FinancedGoalView;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException.Rule;
import com.personaltracker.goals.domain.model.CreditPlan;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.PaymentSchedule.Installment;
import com.personaltracker.goals.domain.model.PrepaymentMode;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra los pagos de cuotas y los abonos extraordinarios de una meta financiada (ADR-0018). Son
 * marcas dentro de la meta: no crean movimientos en Finanzas.
 */
@Service
public class RecordCreditPayments {

    /** Antigüedad máxima de la fecha de un pago o abono respecto a hoy. */
    static final int MAX_AGE_YEARS = 100;

    private final FinancedGoalReader reader;
    private final GoalCalendar calendar;
    private final Clock clock;

    RecordCreditPayments(FinancedGoalReader reader, GoalCalendar calendar, Clock clock) {
        this.reader = reader;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * Paga la siguiente cuota pendiente. Lo que supere el valor de la cuota es un abono extraordinario
     * que se aplica con {@code excessMode}.
     *
     * <p>404 si la meta no existe o es de otro usuario; 422 si el pago es menor que la cuota, supera lo
     * que falta, la fecha no es válida o el crédito ya está pagado.
     */
    @Transactional
    public FinancedGoalView pay(UserId owner, FinancedGoalId goalId, LocalDate date, Money amount,
            PrepaymentMode excessMode) {
        FinancedGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        requireDate(date, today);
        FinancedRecord payment = new InstallmentPayment(UUID.randomUUID(), date, amount, excessMode, clock.instant());
        return reader.append(goal, reader.records(goal), List.of(payment), today);
    }

    /**
     * Marca como pagadas todas las cuotas pendientes hasta {@code installmentNumber}, cada una por su
     * valor programado y en su fecha de vencimiento (o la de hoy, si aún no vence). Sirve para
     * registrar un crédito que ya empezó.
     *
     * <p>422 si esa cuota ya está pagada o no existe en el plan.
     */
    @Transactional
    public FinancedGoalView payThrough(UserId owner, FinancedGoalId goalId, int installmentNumber) {
        FinancedGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        List<FinancedRecord> existing = reader.records(goal);
        CreditPlan plan = CreditPlan.of(goal.terms(), existing);
        List<Installment> pending = plan.schedule().installments().stream()
                .filter(installment -> !installment.paid() && installment.number() <= installmentNumber)
                .toList();
        boolean exists = plan.schedule().installments().stream()
                .anyMatch(installment -> installment.number() == installmentNumber);
        if (pending.isEmpty() || !exists) {
            throw new InvalidFinancedRecordException(Rule.INSTALLMENT_NUMBER,
                    "Elige una cuota pendiente del plan para marcar los pagos hasta ella.");
        }
        List<FinancedRecord> payments = pending.stream()
                .<FinancedRecord>map(installment -> new InstallmentPayment(UUID.randomUUID(),
                        installment.dueDate().isAfter(today) ? today : installment.dueDate(),
                        installment.payment(), PrepaymentMode.REDUCE_TERM, clock.instant()))
                .toList();
        return reader.append(goal, existing, payments, today);
    }

    /**
     * Abono extraordinario: va completo a capital y, según {@code mode}, reduce el plazo o la cuota.
     *
     * <p>422 si el abono supera el saldo, la fecha no es válida o el crédito ya está pagado.
     */
    @Transactional
    public FinancedGoalView prepay(UserId owner, FinancedGoalId goalId, LocalDate date, Money amount,
            PrepaymentMode mode) {
        FinancedGoal goal = reader.require(owner, goalId);
        LocalDate today = calendar.today(owner);
        requireDate(date, today);
        FinancedRecord prepayment = new Prepayment(UUID.randomUUID(), date, amount, mode, clock.instant());
        return reader.append(goal, reader.records(goal), List.of(prepayment), today);
    }

    /** La fecha es informativa, pero no puede ser futura ni de hace más de {@link #MAX_AGE_YEARS} años. */
    private static void requireDate(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new InvalidFinancedRecordException(Rule.DATE, "La fecha no puede ser futura.");
        }
        if (date.isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new InvalidFinancedRecordException(Rule.DATE,
                    "La fecha no puede tener más de " + MAX_AGE_YEARS + " años.");
        }
    }
}
