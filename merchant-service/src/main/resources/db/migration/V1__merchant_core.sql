CREATE TABLE merchants (
    id                    UUID PRIMARY KEY,
    merchant_code         VARCHAR(32)  NOT NULL UNIQUE,
    business_name         VARCHAR(200) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    phone                 VARCHAR(32),
    status                VARCHAR(16)  NOT NULL,
    country               CHAR(2)      NOT NULL,
    default_currency      CHAR(3)      NOT NULL,
    fee_percentage        NUMERIC(5,2) NOT NULL DEFAULT 2.00,
    fixed_fee             NUMERIC(19,4) NOT NULL DEFAULT 0,
    settlement_delay_days INTEGER      NOT NULL DEFAULT 2,
    live_mode_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    status_reason         VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_merchant_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','BLOCKED')),
    -- Money invariants enforced by the database, not only by bean validation.
    CONSTRAINT chk_merchant_fee_pct CHECK (fee_percentage >= 0 AND fee_percentage <= 100),
    CONSTRAINT chk_merchant_fixed_fee CHECK (fixed_fee >= 0),
    CONSTRAINT chk_merchant_settlement_delay CHECK (settlement_delay_days BETWEEN 0 AND 90)
);
CREATE UNIQUE INDEX uk_merchants_email_lower ON merchants (LOWER(email));
CREATE INDEX idx_merchants_status ON merchants (status);

CREATE TABLE api_keys (
    id          UUID PRIMARY KEY,
    key_id      VARCHAR(64)  NOT NULL UNIQUE,
    lookup_hash VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256(pepper||raw), indexed
    secret_hash VARCHAR(255) NOT NULL,          -- BCrypt(raw), the real check
    masked_key  VARCHAR(64)  NOT NULL,
    merchant_id UUID         NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    environment VARCHAR(8)   NOT NULL,
    key_type    VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    label       VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_by  UUID,
    CONSTRAINT chk_api_key_env CHECK (environment IN ('TEST','LIVE')),
    CONSTRAINT chk_api_key_type CHECK (key_type IN ('PUBLISHABLE','SECRET')),
    CONSTRAINT chk_api_key_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED'))
);
CREATE INDEX idx_api_keys_merchant ON api_keys (merchant_id);
CREATE INDEX idx_api_keys_active ON api_keys (merchant_id) WHERE status = 'ACTIVE';

CREATE TABLE api_key_scopes (
    api_key_id UUID        NOT NULL REFERENCES api_keys (id) ON DELETE CASCADE,
    scope      VARCHAR(64) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);
