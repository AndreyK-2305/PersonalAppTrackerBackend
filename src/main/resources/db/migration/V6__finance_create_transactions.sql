-- Movimientos (ADR-0013). Una sola tabla para la jerarquía Transaction; type indica la subclase y
-- type_fields_check fija qué columnas lleva cada tipo:
--   INCOME:     solo destino.
--   EXPENSE:    solo origen.
--   TRANSFER:   origen y destino distintos, sin categoría.
--   ADJUSTMENT: destino si aumenta el saldo u origen si lo disminuye, sin categoría.
-- Las llaves foráneas incluyen user_id: un movimiento no puede apuntar a productos ni categorías de
-- otro usuario (ADR-0003). Los saldos y reportes se calculan con sumas sobre esta tabla.
CREATE TABLE finance.transactions (
    id                     uuid          PRIMARY KEY,
    user_id                uuid          NOT NULL,
    type                   varchar(20)   NOT NULL,
    amount                 numeric(19,2) NOT NULL,
    currency               varchar(3)    NOT NULL,
    transaction_date       date          NOT NULL,
    source_account_id      uuid,
    destination_account_id uuid,
    category_id            uuid,
    description            varchar(200),
    merchant               varchar(100),
    channel                varchar(20),
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL,
    CONSTRAINT transactions_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT transactions_source_fk FOREIGN KEY (source_account_id, user_id)
        REFERENCES finance.accounts (id, user_id),
    CONSTRAINT transactions_destination_fk FOREIGN KEY (destination_account_id, user_id)
        REFERENCES finance.accounts (id, user_id),
    CONSTRAINT transactions_category_fk FOREIGN KEY (category_id, user_id)
        REFERENCES finance.categories (id, user_id),
    CONSTRAINT transactions_type_check CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ADJUSTMENT')),
    CONSTRAINT transactions_amount_check CHECK (amount > 0),
    CONSTRAINT transactions_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT transactions_channel_check CHECK (channel IN
        ('CARD', 'BANK_TRANSFER', 'PSE', 'CASH', 'DEPOSIT', 'WITHDRAWAL', 'DIRECT_DEBIT', 'OTHER')),
    CONSTRAINT transactions_type_fields_check CHECK (
        CASE type
            WHEN 'INCOME' THEN source_account_id IS NULL AND destination_account_id IS NOT NULL
            WHEN 'EXPENSE' THEN source_account_id IS NOT NULL AND destination_account_id IS NULL
            WHEN 'TRANSFER' THEN source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
                AND source_account_id <> destination_account_id AND category_id IS NULL
            WHEN 'ADJUSTMENT' THEN (source_account_id IS NULL) <> (destination_account_id IS NULL)
                AND category_id IS NULL
        END)
);

-- Listados y reportes por usuario y fecha.
CREATE INDEX transactions_user_date_idx ON finance.transactions (user_id, transaction_date);
-- Búsquedas por producto y por categoría (también aceleran las llaves foráneas).
CREATE INDEX transactions_source_idx ON finance.transactions (source_account_id);
CREATE INDEX transactions_destination_idx ON finance.transactions (destination_account_id);
CREATE INDEX transactions_category_idx ON finance.transactions (category_id);
