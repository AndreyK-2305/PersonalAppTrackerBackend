package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException;
import com.personaltracker.goals.domain.exception.InvalidSavingsGoalException.Rule;
import com.personaltracker.shared.domain.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Cómo se planea llegar al objetivo (ADR-0018): con una fecha, y la app calcula el aporte mensual,
 * o con un aporte mensual, y la app estima la fecha.
 */
public sealed interface SavingsPlan {

    SavingsPlanKind kind();

    /** Llegar al objetivo en {@code targetDate}. */
    record ByTargetDate(LocalDate targetDate) implements SavingsPlan {

        public ByTargetDate {
            Objects.requireNonNull(targetDate, "targetDate");
        }

        @Override
        public SavingsPlanKind kind() {
            return SavingsPlanKind.TARGET_DATE;
        }
    }

    /** Apartar {@code monthlyAmount} cada mes. */
    record ByMonthlyAmount(Money monthlyAmount) implements SavingsPlan {

        public ByMonthlyAmount {
            Objects.requireNonNull(monthlyAmount, "monthlyAmount");
            if (!monthlyAmount.isPositive()) {
                throw new InvalidSavingsGoalException(Rule.MONTHLY_AMOUNT,
                        "El aporte mensual debe ser mayor que cero.");
            }
        }

        @Override
        public SavingsPlanKind kind() {
            return SavingsPlanKind.MONTHLY_AMOUNT;
        }
    }
}
