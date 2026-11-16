-- Metas financiadas (ADR-0018, entrega 2): un bien que se piensa comprar a crédito y las condiciones
-- de la financiación. La tabla de amortización no se guarda: se calcula al consultar.
--   down_payment: cuota inicial; 0 si no hay. Siempre menor que el valor del bien.
--   annual_rate: tasa efectiva anual como fracción (0.18 = 18 %).
--   actual_installment: cuota real que cobra la entidad; nula si se usa la calculada. La diferencia
--     con la calculada se muestra como "seguros y otros".
-- Las restricciones nombran de forma explícita los nulos: en SQL, una comparación con NULL no es
-- falsa sino desconocida, y un CHECK desconocido se da por cumplido.
CREATE TABLE goals.financed_goals (
    id                 uuid          PRIMARY KEY,
    user_id            uuid          NOT NULL,
    name               varchar(100)  NOT NULL,
    asset_value        numeric(19,2) NOT NULL,
    down_payment       numeric(19,2) NOT NULL,
    currency           varchar(3)    NOT NULL,
    annual_rate        numeric(9,6)  NOT NULL,
    installments       integer       NOT NULL,
    first_due_date     date          NOT NULL,
    actual_installment numeric(19,2),
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT financed_goals_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT financed_goals_asset_value_positive CHECK (asset_value > 0),
    CONSTRAINT financed_goals_down_payment_range CHECK (down_payment >= 0 AND down_payment < asset_value),
    CONSTRAINT financed_goals_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT financed_goals_rate_range CHECK (annual_rate >= 0 AND annual_rate <= 10),
    CONSTRAINT financed_goals_installments_range CHECK (installments BETWEEN 1 AND 600),
    CONSTRAINT financed_goals_actual_installment_positive
        CHECK (actual_installment IS NULL OR actual_installment > 0)
);

CREATE INDEX financed_goals_user_idx ON goals.financed_goals (user_id, created_at DESC);
