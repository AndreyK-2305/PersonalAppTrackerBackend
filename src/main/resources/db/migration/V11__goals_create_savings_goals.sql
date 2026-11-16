-- Esquema del módulo goals (ADR-0018): metas de ahorro, sus abonos y sus cambios de rentabilidad.
-- Los abonos son marcas dentro de la meta: no hay relación con finance.
CREATE SCHEMA goals;

-- Meta de ahorro. plan_kind decide qué campo del plan se usa:
--   TARGET_DATE: target_date (la app calcula el aporte mensual);
--   MONTHLY_AMOUNT: monthly_amount (la app estima la fecha).
CREATE TABLE goals.savings_goals (
    id             uuid          PRIMARY KEY,
    user_id        uuid          NOT NULL,
    name           varchar(100)  NOT NULL,
    target_amount  numeric(19,2) NOT NULL,
    currency       varchar(3)    NOT NULL,
    plan_kind      varchar(20)   NOT NULL,
    target_date    date,
    monthly_amount numeric(19,2),
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT savings_goals_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT savings_goals_target_positive CHECK (target_amount > 0),
    CONSTRAINT savings_goals_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT savings_goals_plan CHECK (
        (plan_kind = 'TARGET_DATE' AND target_date IS NOT NULL AND monthly_amount IS NULL)
        OR (plan_kind = 'MONTHLY_AMOUNT' AND monthly_amount > 0 AND target_date IS NULL))
);

CREATE INDEX savings_goals_user_idx ON goals.savings_goals (user_id, created_at DESC);

-- Abono marcado por el usuario. Se borra con la meta.
CREATE TABLE goals.savings_contributions (
    id                uuid          PRIMARY KEY,
    goal_id           uuid          NOT NULL,
    user_id           uuid          NOT NULL,
    contribution_date date          NOT NULL,
    amount            numeric(19,2) NOT NULL,
    currency          varchar(3)    NOT NULL,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT savings_contributions_goal_fk
        FOREIGN KEY (goal_id) REFERENCES goals.savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT savings_contributions_amount_positive CHECK (amount > 0),
    CONSTRAINT savings_contributions_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX savings_contributions_goal_idx ON goals.savings_contributions (goal_id, contribution_date DESC);

-- Rentabilidad EA (fracción: 0.105 = 10,5 %) desde effective_from. 0 la apaga. Un cambio por día de
-- inicio: un cambio nuevo reemplaza al que aún no rige.
CREATE TABLE goals.savings_rate_changes (
    id             uuid         PRIMARY KEY,
    goal_id        uuid         NOT NULL,
    user_id        uuid         NOT NULL,
    effective_from date         NOT NULL,
    annual_rate    numeric(9,6) NOT NULL,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT savings_rate_changes_goal_fk
        FOREIGN KEY (goal_id) REFERENCES goals.savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT savings_rate_changes_rate_range CHECK (annual_rate >= 0 AND annual_rate <= 10),
    CONSTRAINT savings_rate_changes_goal_day UNIQUE (goal_id, effective_from)
);
