package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/**
 * Un pago, abono o ajuste de tasa de una meta financiada no cumple las reglas (ADR-0018). Cada regla
 * tiene su código estable para que el cliente sepa qué corregir.
 */
public class InvalidFinancedRecordException extends DomainException {

    /** Regla que incumple el registro. */
    public enum Rule {
        PAYMENT_AMOUNT("goals.financed_record.payment_amount_invalid"),
        PREPAYMENT_AMOUNT("goals.financed_record.prepayment_amount_invalid"),
        DATE("goals.financed_record.date_invalid"),
        ACTUAL_INSTALLMENT("goals.financed_record.actual_installment_invalid"),
        INSTALLMENT_NUMBER("goals.financed_record.installment_number_invalid"),
        /** El crédito ya está pagado: no admite más registros. */
        PAID_OFF("goals.financed_record.credit_paid_off"),
        /** Solo se puede deshacer el último registro. */
        NOT_LAST("goals.financed_record.not_last");

        private final String code;

        Rule(String code) {
            this.code = code;
        }
    }

    public InvalidFinancedRecordException(Rule rule, String message) {
        super(rule.code, message);
    }
}
