-- Origen de los movimientos (ADR-0017): MANUAL o CAPTURE (captado de una notificación bancaria).
-- amount_estimated marca los montos convertidos de dólares con la TRM, que no son el valor final
-- del banco; deja de ser verdadero cuando el usuario corrige el monto.
ALTER TABLE finance.transactions
    ADD COLUMN origin varchar(10) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN amount_estimated boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT transactions_origin_check CHECK (origin IN ('MANUAL', 'CAPTURE')),
    ADD CONSTRAINT transactions_estimated_check CHECK (NOT amount_estimated OR origin = 'CAPTURE');
