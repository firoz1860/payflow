-- ---------------------------------------------------------------------------
-- payment-service schema.
-- Money is NUMERIC(19,4) everywhere - never float/double, never an int of cents
-- that some services interpret differently.
-- ---------------------------------------------------------------------------

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    payment_reference   VARCHAR(40)   NOT NULL UNIQUE,
    merchant_id         UUID          NOT NULL,
    customer_id         UUID,
    merchant_order_id   VARCHAR(128),
    amount              NUMERIC(19,4) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    refunded_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    status              VARCHAR(24)   NOT NULL,
    environment         VARCHAR(8)    NOT NULL,
    description         VARCHAR(500),
    metadata            JSONB,
    provider            VARCHAR(32),
    provider_payment_id VARCHAR(128),
    checkout_url        VARCHAR(1000),
    failure_code        VARCHAR(64),
    failure_message     VARCHAR(500),
    risk_decision       VARCHAR(16),
    risk_score          INTEGER,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ   NOT NULL,
    authorized_at       TIMESTAMPTZ,
    captured_at         TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_payment_status CHECK (status IN
        ('CREATED','PENDING','PROCESSING','AUTHORIZED','CAPTURED','FAILED',
         'CANCELLED','PARTIALLY_REFUNDED','REFUNDED')),
    CONSTRAINT chk_payment_env CHECK (environment IN ('TEST','LIVE')),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_refund_non_negative CHECK (refunded_amount >= 0),

    -- The over-refund invariant, enforced by the database itself. Even if every
    -- Java guard were bypassed, PostgreSQL would reject the row.
    CONSTRAINT chk_payment_refund_not_exceeding CHECK (refunded_amount <= amount)
);

-- One order id per merchant: a merchant replaying the same order cannot double charge.
CREATE UNIQUE INDEX uk_payments_merchant_order
    ON payments (merchant_id, merchant_order_id)
    WHERE merchant_order_id IS NOT NULL;

CREATE UNIQUE INDEX uk_payments_provider_ref
    ON payments (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments (status);
-- Supports the expiry sweeper without scanning the whole table.
CREATE INDEX idx_payments_pending_expiry ON payments (expires_at)
    WHERE status IN ('CREATED','PENDING','PROCESSING');

CREATE TABLE payment_attempts (
    id                   UUID PRIMARY KEY,
    payment_id           UUID          NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    attempt_number       INTEGER       NOT NULL,
    provider             VARCHAR(32)   NOT NULL,
    provider_payment_id  VARCHAR(128),
    payment_method       VARCHAR(16),
    payment_method_token VARCHAR(128),   -- provider token only; never a PAN
    card_last4           CHAR(4),
    card_network         VARCHAR(32),
    amount               NUMERIC(19,4) NOT NULL,
    currency             CHAR(3)       NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    failure_code         VARCHAR(64),
    failure_message      VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_attempt_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT chk_attempt_status CHECK (status IN
        ('INITIATED','PENDING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT chk_attempt_method CHECK (payment_method IS NULL OR payment_method IN
        ('CARD','UPI','NET_BANKING','WALLET')),
    CONSTRAINT chk_attempt_amount CHECK (amount > 0),
    -- Storing 4 digits is permitted; storing anything longer would mean a PAN leaked in.
    CONSTRAINT chk_attempt_last4 CHECK (card_last4 IS NULL OR card_last4 ~ '^[0-9]{4}$')
);
CREATE INDEX idx_attempts_payment ON payment_attempts (payment_id, attempt_number);
CREATE INDEX idx_attempts_provider_ref ON payment_attempts (provider, provider_payment_id);

CREATE TABLE idempotency_records (
    id              UUID PRIMARY KEY,
    merchant_id     UUID         NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    endpoint        VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    resource_id     VARCHAR(64),
    response_code   INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ  NOT NULL,

    -- THE mechanism. Two concurrent retries race to insert; exactly one wins and
    -- the loser replays the winner's response instead of creating a second payment.
    CONSTRAINT uk_idempotency_merchant_key UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT chk_idempotency_status CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED'))
);
CREATE INDEX idx_idempotency_expiry ON idempotency_records (expires_at);
