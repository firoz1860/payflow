-- ---------------------------------------------------------------------------
-- Transactional outbox. Written in the same transaction as the business change.
-- Identical in every publishing service.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(128) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

-- Partial index: the publisher only ever scans PENDING rows, so the index stays
-- small even after millions of published events.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
