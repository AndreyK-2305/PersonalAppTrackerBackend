-- Esquema del módulo capture (ADR-0005 y ADR-0017): cola de notificaciones bancarias y llaves Bre-B
-- propias.
CREATE SCHEMA capture;

-- Notificaciones recibidas de los dispositivos del usuario y lo que se hizo con cada una.
--   title y body: texto crudo. Se borra a los 45 días (raw_purged_at); los datos extraídos quedan.
--   content_hash: SHA-256 de app, título, texto y hora de recepción. Un reenvío exacto del atajo
--     choca con la clave única y se ignora.
--   transaction_id: movimiento de finance que generó. Sin llave foránea entre módulos; al borrar el
--     movimiento, la notificación pasa a DISCARDED (ADR-0015).
--   paired_with: notificación de otra fuente que ya registró la misma compra. La clave única
--     parcial garantiza el emparejamiento de uno a uno.
--   product_id, amount, currency, source, occurred_at: huella para emparejar (todas o ninguna).
CREATE TABLE capture.notifications (
    id             uuid          PRIMARY KEY,
    user_id        uuid          NOT NULL,
    app            varchar(100)  NOT NULL,
    title          varchar(500),
    body           text,
    received_at    timestamptz   NOT NULL,
    content_hash   varchar(64)   NOT NULL,
    status         varchar(10)   NOT NULL,
    review_reason  varchar(30),
    transaction_id uuid,
    paired_with    uuid,
    product_id     uuid,
    amount         numeric(19,2),
    currency       varchar(3),
    source         varchar(20),
    occurred_at    timestamptz,
    raw_purged_at  timestamptz,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL,
    CONSTRAINT notifications_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT notifications_paired_with_fk FOREIGN KEY (paired_with) REFERENCES capture.notifications (id),
    CONSTRAINT notifications_content_key UNIQUE (user_id, content_hash),
    CONSTRAINT notifications_status_check CHECK (status IN ('RECEIVED', 'RECORDED', 'REVIEW', 'PAIRED', 'DISCARDED')),
    CONSTRAINT notifications_review_reason_check CHECK (review_reason IN
        ('UNRECOGNIZED_TEXT', 'AMBIGUOUS_PRODUCT', 'POSSIBLE_OWN_TRANSFER', 'MISSING_EXCHANGE_RATE')),
    CONSTRAINT notifications_review_check CHECK ((status = 'REVIEW') = (review_reason IS NOT NULL)),
    CONSTRAINT notifications_recorded_check CHECK (status <> 'RECORDED' OR transaction_id IS NOT NULL),
    CONSTRAINT notifications_paired_check CHECK ((status = 'PAIRED') = (paired_with IS NOT NULL)),
    CONSTRAINT notifications_source_check CHECK (source IN ('BANCOLOMBIA_SMS', 'RAPPI_PUSH', 'APPLE_WALLET')),
    CONSTRAINT notifications_amount_check CHECK (amount > 0),
    CONSTRAINT notifications_footprint_check CHECK (
        (product_id IS NULL AND amount IS NULL AND currency IS NULL AND source IS NULL AND occurred_at IS NULL)
        OR (product_id IS NOT NULL AND amount IS NOT NULL AND currency IS NOT NULL AND source IS NOT NULL
            AND occurred_at IS NOT NULL))
);

-- Cola: notificaciones pendientes por orden de llegada.
CREATE INDEX notifications_pending_idx ON capture.notifications (created_at) WHERE status = 'RECEIVED';
-- Emparejamiento: movimientos captados del usuario por fecha.
CREATE INDEX notifications_pairing_idx ON capture.notifications (user_id, occurred_at) WHERE occurred_at IS NOT NULL;
-- Cada notificación se empareja con una sola.
CREATE UNIQUE INDEX notifications_paired_with_key ON capture.notifications (paired_with) WHERE paired_with IS NOT NULL;
-- Limpieza del texto crudo a los 45 días.
CREATE INDEX notifications_purge_idx ON capture.notifications (received_at) WHERE raw_purged_at IS NULL;

-- Llaves Bre-B propias (Ajustes → Perfil), cada una con el producto propio al que llega. La llave se
-- guarda normalizada (sin espacios a los lados y en minúsculas). product_id es un identificador de
-- finance, sin llave foránea entre módulos: una llave de un producto borrado o archivado se ignora.
CREATE TABLE capture.own_keys (
    id         uuid         PRIMARY KEY,
    user_id    uuid         NOT NULL,
    key_value  varchar(100) NOT NULL,
    product_id uuid         NOT NULL,
    created_at timestamptz  NOT NULL,
    CONSTRAINT own_keys_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT own_keys_user_key UNIQUE (user_id, key_value)
);
