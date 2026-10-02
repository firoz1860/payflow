CREATE TABLE razorpay_order_attempts (
    key_hash VARCHAR(64) PRIMARY KEY,
    payment_reference VARCHAR(64) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('CREATING','CREATED','RECONCILIATION_REQUIRED')),
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((state = 'CREATED') = (response_json IS NOT NULL))
);
