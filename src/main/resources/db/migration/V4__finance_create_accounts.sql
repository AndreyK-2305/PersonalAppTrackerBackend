-- Esquema del módulo finance (ADR-0005) y productos del usuario (ADR-0013 y ADR-0014).
CREATE SCHEMA finance;

-- Una sola tabla para la jerarquía Account; kind indica la subclase y las restricciones
-- kind_fields_check fijan qué columnas lleva cada tipo. El saldo no se guarda: se deriva de los
-- movimientos. La deuda inicial de una tarjeta es un saldo inicial negativo.
CREATE TABLE finance.accounts (
    id               uuid          PRIMARY KEY,
    user_id          uuid          NOT NULL,
    kind             varchar(20)   NOT NULL,
    name             varchar(60)   NOT NULL,
    institution      varchar(60),
    last_four_digits varchar(4),
    currency         varchar(3)    NOT NULL,
    opening_balance  numeric(19,2) NOT NULL,
    credit_limit     numeric(19,2),
    archived_at      timestamptz,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL,
    CONSTRAINT accounts_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    -- Permite que otras tablas referencien el producto junto con su dueño (ADR-0003).
    CONSTRAINT accounts_id_user_key UNIQUE (id, user_id),
    CONSTRAINT accounts_kind_check
        CHECK (kind IN ('CASH', 'SAVINGS', 'CHECKING', 'DIGITAL_WALLET', 'CREDIT_CARD', 'OTHER')),
    CONSTRAINT accounts_name_check CHECK (length(btrim(name)) > 0),
    -- Nunca se guarda el número completo de una tarjeta o cuenta.
    CONSTRAINT accounts_last_four_digits_check CHECK (last_four_digits ~ '^[0-9]{4}$'),
    CONSTRAINT accounts_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT accounts_kind_fields_check CHECK (
        CASE kind
            WHEN 'CASH' THEN institution IS NULL AND last_four_digits IS NULL AND credit_limit IS NULL
                AND opening_balance >= 0
            WHEN 'CREDIT_CARD' THEN institution IS NOT NULL AND credit_limit > 0 AND opening_balance <= 0
            WHEN 'OTHER' THEN institution IS NULL AND last_four_digits IS NULL AND credit_limit IS NULL
                AND opening_balance = 0 AND archived_at IS NULL
            ELSE institution IS NOT NULL AND credit_limit IS NULL
        END)
);

-- Nombre único por usuario sin distinguir mayúsculas; reserva también el nombre de "Otro".
CREATE UNIQUE INDEX accounts_user_name_key ON finance.accounts (user_id, lower(name));

-- Un solo producto "Otro" por usuario (ADR-0014).
CREATE UNIQUE INDEX accounts_user_other_key ON finance.accounts (user_id) WHERE kind = 'OTHER';
