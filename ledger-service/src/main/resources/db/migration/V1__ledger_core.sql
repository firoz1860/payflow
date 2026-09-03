-- ---------------------------------------------------------------------------
-- Double-entry ledger.
-- Note what is absent: there is NO balance column anywhere. Balances are always
-- derived by summing entries. A stored balance can drift away from the entries
-- that produced it; a derived one cannot.
-- ---------------------------------------------------------------------------

CREATE TABLE ledger_accounts (
    id           UUID PRIMARY KEY,
    owner_type   VARCHAR(16) NOT NULL,
    owner_id     VARCHAR(64) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency     CHAR(3)     NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ledger_account_identity UNIQUE (owner_type, owner_id, account_type, currency),
    CONSTRAINT chk_ledger_owner_type CHECK (owner_type IN ('PLATFORM','MERCHANT','CUSTOMER','PROVIDER')),
    CONSTRAINT chk_ledger_account_type CHECK (account_type IN
        ('PAYMENT_CLEARING','PLATFORM_CASH','MERCHANT_PAYABLE','FEE_REVENUE',
         'TAX_PAYABLE','REFUND_CLEARING','MERCHANT_RESERVE')),
    CONSTRAINT chk_ledger_account_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);
CREATE INDEX idx_ledger_accounts_owner ON ledger_accounts (owner_id);

CREATE TABLE ledger_postings (
    id                  UUID PRIMARY KEY,
    source_type         VARCHAR(24)   NOT NULL,
    source_id           VARCHAR(64)   NOT NULL,
    merchant_id         VARCHAR(64),
    currency            CHAR(3)       NOT NULL,
    total_debit         NUMERIC(19,4) NOT NULL,
    total_credit        NUMERIC(19,4) NOT NULL,
    description         VARCHAR(255),
    reverses_posting_id UUID REFERENCES ledger_postings (id),
    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Idempotency under at-least-once event delivery: one posting per source event.
    CONSTRAINT uk_posting_source UNIQUE (source_type, source_id),
    CONSTRAINT chk_posting_source_type CHECK (source_type IN
        ('PAYMENT','REFUND','FEE','SETTLEMENT','ADJUSTMENT','REVERSAL')),

    -- The double-entry invariant, enforced by PostgreSQL itself. Even a direct
    -- psql INSERT cannot create an unbalanced posting.
    CONSTRAINT chk_posting_balanced CHECK (total_debit = total_credit),
    CONSTRAINT chk_posting_non_zero CHECK (total_debit > 0)
);
CREATE INDEX idx_postings_merchant ON ledger_postings (merchant_id, created_at DESC);
CREATE INDEX idx_postings_currency ON ledger_postings (currency);

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    posting_id  UUID          NOT NULL REFERENCES ledger_postings (id),
    account_id  UUID          NOT NULL REFERENCES ledger_accounts (id),
    entry_type  VARCHAR(8)    NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    currency    CHAR(3)       NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT','CREDIT')),
    -- Direction is carried by entry_type. A negative amount would be a second,
    -- contradictory way to express direction, so it is forbidden outright.
    CONSTRAINT chk_entry_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_entries_account ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_entries_posting ON ledger_entries (posting_id);

-- Immutability enforced at the database level, not merely by convention in Java.
-- Application bugs, ad-hoc psql sessions and future services are all covered.
CREATE OR REPLACE FUNCTION reject_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries are immutable: post a reversing entry instead of % ', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_no_update
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_postings_no_delete
    BEFORE DELETE ON ledger_postings
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
