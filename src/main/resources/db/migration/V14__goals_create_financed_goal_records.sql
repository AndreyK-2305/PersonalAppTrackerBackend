-- Registros de una meta financiada (ADR-0018, entrega 3): pagos de cuotas, abonos extraordinarios a
-- capital y ajustes de tasa. El plan de pagos se calcula repasándolos en orden (sequence) sobre las
-- condiciones de la meta, así que el orden es único por meta. Se borran con la meta.
--   PAYMENT: amount es lo pagado; lo que supere la cuota es un abono que se aplica con prepayment_mode.
--   PREPAYMENT: amount va completo a capital; prepayment_mode decide si reduce el plazo o la cuota.
--   RATE_ADJUSTMENT: annual_rate (fracción) rige desde la siguiente cuota; actual_installment es la
--     nueva cuota real, o nulo para conservar los mismos seguros y otros.
-- La restricción de cada tipo nombra de forma explícita los nulos: una comparación con NULL es
-- desconocida y un CHECK desconocido se da por cumplido.
CREATE TABLE goals.financed_goal_records (
    id                 uuid          PRIMARY KEY,
    goal_id            uuid          NOT NULL,
    user_id            uuid          NOT NULL,
    sequence           integer       NOT NULL,
    kind               varchar(20)   NOT NULL,
    record_date        date          NOT NULL,
    currency           varchar(3)    NOT NULL,
    amount             numeric(19,2),
    prepayment_mode    varchar(20),
    annual_rate        numeric(9,6),
    actual_installment numeric(19,2),
    created_at         timestamptz   NOT NULL,
    CONSTRAINT financed_goal_records_goal_fk
        FOREIGN KEY (goal_id) REFERENCES goals.financed_goals (id) ON DELETE CASCADE,
    CONSTRAINT financed_goal_records_order UNIQUE (goal_id, sequence),
    CONSTRAINT financed_goal_records_sequence_positive CHECK (sequence >= 1),
    CONSTRAINT financed_goal_records_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT financed_goal_records_mode_check
        CHECK (prepayment_mode IS NULL OR prepayment_mode IN ('REDUCE_TERM', 'REDUCE_INSTALLMENT')),
    CONSTRAINT financed_goal_records_kind CHECK (
        (kind IN ('PAYMENT', 'PREPAYMENT')
            AND amount IS NOT NULL AND amount > 0
            AND prepayment_mode IS NOT NULL
            AND annual_rate IS NULL AND actual_installment IS NULL)
        OR (kind = 'RATE_ADJUSTMENT'
            AND amount IS NULL AND prepayment_mode IS NULL
            AND annual_rate IS NOT NULL AND annual_rate >= 0 AND annual_rate <= 10
            AND (actual_installment IS NULL OR actual_installment > 0)))
);
