-- Persist retry eligibility so a brief delivery outage cannot exhaust all attempts in seconds.
-- Existing rows remain immediately eligible; old application instances can still insert rows.
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
DROP INDEX idx_outbox_pending;
CREATE INDEX idx_outbox_pending ON outbox_events (next_attempt_at, created_at) WHERE status = 'PENDING';
