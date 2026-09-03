CREATE TABLE provider_events (
    id                  UUID PRIMARY KEY,
    provider            VARCHAR(32)  NOT NULL,
    provider_event_id   VARCHAR(128) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    provider_payment_id VARCHAR(128),
    payload             JSONB        NOT NULL,
    processing_status   VARCHAR(16)  NOT NULL,
    failure_reason      VARCHAR(1000),
    received_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,

    -- The deduplication guarantee. A provider redelivering "payment.captured"
    -- ten times results in exactly one row and one downstream event.
    CONSTRAINT uk_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_provider_event_status CHECK (processing_status IN
        ('RECEIVED','PROCESSED','FAILED','IGNORED'))
);
CREATE INDEX idx_provider_events_payment ON provider_events (provider, provider_payment_id);
CREATE INDEX idx_provider_events_received ON provider_events (received_at DESC);
