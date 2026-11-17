package com.personaltracker.goals.domain.model;

import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException;
import com.personaltracker.goals.domain.exception.InvalidFinancedRecordException.Rule;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.finance.AnnualRate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Algo que pasó en un crédito después de crearlo (ADR-0018): el pago de una cuota, un abono
 * extraordinario a capital o un ajuste de tasa. Los registros se guardan en orden y el plan de pagos
 * se obtiene repasándolos sobre las condiciones originales ({@link CreditPlan}).
 */
public sealed interface FinancedRecord {

    /** Tipo de registro; es el valor que viaja en la API y se guarda en la base. */
    enum Kind {
        PAYMENT,
        PREPAYMENT,
        RATE_ADJUSTMENT
    }

    UUID id();

    /** Fecha del pago o del abono, o el día en que se registró el ajuste. Es informativa. */
    LocalDate date();

    Instant createdAt();

    Kind kind();

    /**
     * Pago de la siguiente cuota pendiente. Si {@code amount} supera el valor de la cuota, el exceso es
     * un abono extraordinario que se aplica con {@code excessMode}.
     */
    record InstallmentPayment(UUID id, LocalDate date, Money amount, PrepaymentMode excessMode, Instant createdAt)
            implements FinancedRecord {

        public InstallmentPayment {
            requireCommon(id, date, createdAt);
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(excessMode, "excessMode");
            if (!amount.isPositive()) {
                throw new InvalidFinancedRecordException(Rule.PAYMENT_AMOUNT, "El pago debe ser mayor que cero.");
            }
        }

        @Override
        public Kind kind() {
            return Kind.PAYMENT;
        }
    }

    /** Abono extraordinario: va completo a capital. */
    record Prepayment(UUID id, LocalDate date, Money amount, PrepaymentMode mode, Instant createdAt)
            implements FinancedRecord {

        public Prepayment {
            requireCommon(id, date, createdAt);
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(mode, "mode");
            if (!amount.isPositive()) {
                throw new InvalidFinancedRecordException(Rule.PREPAYMENT_AMOUNT, "El abono debe ser mayor que cero.");
            }
        }

        @Override
        public Kind kind() {
            return Kind.PREPAYMENT;
        }
    }

    /**
     * Ajuste de tasa: la nueva tasa EA rige desde la siguiente cuota pendiente.
     *
     * @param actualInstallment nueva cuota real, o {@code null} para conservar los mismos seguros y otros
     */
    record RateAdjustment(UUID id, LocalDate date, AnnualRate rate, Money actualInstallment, Instant createdAt)
            implements FinancedRecord {

        public RateAdjustment {
            requireCommon(id, date, createdAt);
            Objects.requireNonNull(rate, "rate");
        }

        @Override
        public Kind kind() {
            return Kind.RATE_ADJUSTMENT;
        }
    }

    private static void requireCommon(UUID id, LocalDate date, Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
