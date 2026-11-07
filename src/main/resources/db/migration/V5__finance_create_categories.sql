-- Categorías y subcategorías de ingresos y gastos (ADR-0014). Una subcategoría apunta a su
-- categoría principal; la llave foránea incluye el dueño y el tipo, de modo que la base de datos
-- impide colgar una subcategoría de una categoría de otro usuario o de otro tipo. El límite de dos
-- niveles lo valida el dominio.
CREATE TABLE finance.categories (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL,
    kind        varchar(10) NOT NULL,
    name        varchar(50) NOT NULL,
    parent_id   uuid,
    archived_at timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL,
    CONSTRAINT categories_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT categories_id_user_key UNIQUE (id, user_id),
    CONSTRAINT categories_id_user_kind_key UNIQUE (id, user_id, kind),
    CONSTRAINT categories_parent_fk FOREIGN KEY (parent_id, user_id, kind)
        REFERENCES finance.categories (id, user_id, kind),
    CONSTRAINT categories_kind_check CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT categories_name_check CHECK (length(btrim(name)) > 0),
    CONSTRAINT categories_parent_check CHECK (parent_id <> id)
);

-- Nombre único entre las hermanas del mismo tipo, sin distinguir mayúsculas. NULLS NOT DISTINCT
-- hace que la regla también aplique entre categorías principales (parent_id nulo).
CREATE UNIQUE INDEX categories_sibling_name_key
    ON finance.categories (user_id, kind, parent_id, lower(name)) NULLS NOT DISTINCT;

CREATE INDEX categories_parent_idx ON finance.categories (parent_id);
