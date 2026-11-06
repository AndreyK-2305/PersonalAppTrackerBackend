-- Cuentas de usuario (ADR-0011). El email se guarda normalizado (minúsculas, sin espacios).
CREATE TABLE identity.users (
    id                    uuid         PRIMARY KEY,
    email                 varchar(254) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    display_name          varchar(100) NOT NULL,
    time_zone             varchar(64)  NOT NULL,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_failed_login_attempts_check CHECK (failed_login_attempts >= 0)
);
