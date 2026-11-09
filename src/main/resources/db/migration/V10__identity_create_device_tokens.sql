-- Tokens de dispositivo (ADR-0017): el atajo del iPhone los envía como Authorization: Bearer.
--   secret_hash: SHA-256 del secreto; el secreto solo se muestra una vez al crearlo.
--   scopes: permisos separados por espacios, por ejemplo 'finance:capture'.
--   revoked_at: revocación definitiva; el token deja de autenticar pero queda en el historial.
CREATE TABLE identity.device_tokens (
    id           uuid         PRIMARY KEY,
    user_id      uuid         NOT NULL,
    name         varchar(60)  NOT NULL,
    secret_hash  varchar(64)  NOT NULL,
    scopes       varchar(200) NOT NULL,
    created_at   timestamptz  NOT NULL,
    last_used_at timestamptz,
    revoked_at   timestamptz,
    version      bigint       NOT NULL,
    CONSTRAINT device_tokens_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT device_tokens_secret_key UNIQUE (secret_hash),
    CONSTRAINT device_tokens_scopes_check CHECK (scopes <> '')
);

CREATE INDEX device_tokens_user_idx ON identity.device_tokens (user_id);
